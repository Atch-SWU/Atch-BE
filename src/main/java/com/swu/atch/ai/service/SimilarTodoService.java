package com.swu.atch.ai.service;

import com.swu.atch.ai.client.*;
import com.swu.atch.ai.domain.TodoEmbedding;
import com.swu.atch.ai.dto.request.SimilarTodoSearchRequest;
import com.swu.atch.ai.dto.response.*;
import com.swu.atch.ai.dto.response.SimilarTodoSearchResponse.VoyageStatus;
import com.swu.atch.ai.repository.TodoEmbeddingRepository;
import com.swu.atch.common.exception.*;
import com.swu.atch.todo.repository.TodoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SimilarTodoService {
    private final AiTransactions tx;
    private final AiHistory history;
    private final VoyageGateway voyage;
    private final TodoEmbeddingRepository embeddings;
    private final TodoRepository todos;
    private final JdbcTemplate jdbc;
    private final AiRateLimiter limits;

    public record SearchInput(
            String title, String sourceTaskName, String memo, Long excludeTodoId) {
        public String sourceText() {
            return AiJson.title(title)
                    + "\n"
                    + AiJson.text(sourceTaskName)
                    + "\n"
                    + AiJson.text(memo);
        }
    }

    public SimilarTodoSearchResponse search(
            Long user, String stamp, SimilarTodoSearchRequest request) {
        tx.locked(
                user,
                stamp,
                owner -> {
                    if (request.excludeTodoId() != null)
                        todos.findByIdAndUser_IdAndDeletedAtIsNull(request.excludeTodoId(), user)
                                .orElseThrow(
                                        () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
                    limits.acquire(user, false, 1);
                    return null;
                });
        return batch(
                        user,
                        stamp,
                        List.of(
                                new SearchInput(
                                        request.title(),
                                        request.sourceTaskName(),
                                        request.memo(),
                                        request.excludeTodoId())),
                        Instant.now())
                .getFirst();
    }

    // 모든 외부 호출은 User 트랜잭션 밖이다. 묶음 query도 한 요청으로 제한한다.
    public List<SimilarTodoSearchResponse> batch(
            Long user, String stamp, List<SearchInput> inputs, Instant asOf) {
        if (!voyage.enabled()) return empty(inputs, VoyageStatus.DISABLED, asOf);
        var records = tx.locked(user, stamp, owner -> history.eligible(user, asOf));
        if (records.isEmpty()) return empty(inputs, VoyageStatus.NO_HISTORY, asOf);
        try {
            var indexed = metadata(user);
            var missing = records.stream().filter(r -> !matches(indexed, r)).limit(16).toList();
            if (!missing.isEmpty()) {
                var vectors =
                        voyage.embed(
                                missing.stream().map(AiHistory.Observation::sourceText).toList(),
                                "document");
                if (vectors.size() != missing.size())
                    throw new AiProviderException(
                            TimePredictionResult.ClaudeStatus.INVALID_RESPONSE);
                tx.locked(
                        user,
                        stamp,
                        owner -> {
                            var current = new HashMap<Long, AiHistory.Observation>();
                            history.eligible(user, asOf).forEach(r -> current.put(r.todoId(), r));
                            for (int i = 0; i < missing.size(); i++) {
                                var original = missing.get(i);
                                var fresh = current.get(original.todoId());
                                if (fresh == null
                                        || !fresh.sourceHash().equals(original.sourceHash()))
                                    continue;
                                var todo =
                                        todos.findByIdAndUser_IdAndDeletedAtIsNull(
                                                        fresh.todoId(), user)
                                                .orElseThrow();
                                var embedding =
                                        embeddings
                                                .findByTodo_IdAndTodo_User_Id(todo.getId(), user)
                                                .orElseGet(
                                                        () ->
                                                                TodoEmbedding.create(
                                                                        todo,
                                                                        vectors.get(
                                                                                missing.indexOf(
                                                                                        original)),
                                                                        voyage.model(),
                                                                        fresh.sourceHash()));
                                validate(vectors.get(i));
                                embedding.replaceEmbedding(
                                        vectors.get(i), voyage.model(), fresh.sourceHash());
                                embeddings.save(embedding);
                            }
                            embeddings.flush();
                            return null;
                        });
            }
            var queries =
                    voyage.embed(inputs.stream().map(SearchInput::sourceText).toList(), "query");
            if (queries.size() != inputs.size())
                throw new AiProviderException(TimePredictionResult.ClaudeStatus.INVALID_RESPONSE);
            return tx.locked(
                    user,
                    stamp,
                    owner -> {
                        var current = history.eligible(user, asOf);
                        var currentMetadata = metadata(user);
                        var result = new ArrayList<SimilarTodoSearchResponse>();
                        for (int i = 0; i < inputs.size(); i++) {
                            var excluded = inputs.get(i).excludeTodoId();
                            var candidates =
                                    current.stream()
                                            .filter(r -> !Objects.equals(r.todoId(), excluded))
                                            .toList();
                            var valid =
                                    candidates.stream()
                                            .filter(r -> matches(currentMetadata, r))
                                            .toList();
                            boolean partial = valid.size() < candidates.size();
                            List<SimilarTodoSummary> items = new ArrayList<>();
                            if (!valid.isEmpty()) {
                                validate(queries.get(i));
                                var ids =
                                        valid.stream().map(AiHistory.Observation::todoId).toList();
                                var byId = new HashMap<Long, AiHistory.Observation>();
                                valid.forEach(r -> byId.put(r.todoId(), r));
                                var sql =
                                        """
SELECT e.todo_id,e.embedding <=> CAST(:vector AS vector) distance
FROM todo_embeddings e JOIN todos t ON t.id=e.todo_id
WHERE t.user_id=:user AND e.todo_id IN (:ids) AND e.embedding_model=:model
  AND vector_norm(e.embedding)>0
  AND e.source_text_hash=encode(sha256(convert_to(t.title||chr(10)||coalesce(t.source_task_name,'')||chr(10)||coalesce(t.memo,''),'UTF8')),'hex')
ORDER BY distance,e.todo_id LIMIT 3
""";
                                items =
                                        new NamedParameterJdbcTemplate(jdbc)
                                                .query(
                                                        sql,
                                                        Map.of(
                                                                "vector",
                                                                vector(queries.get(i)),
                                                                "user",
                                                                user,
                                                                "ids",
                                                                ids,
                                                                "model",
                                                                voyage.model()),
                                                        (rs, row) -> {
                                                            var r = byId.get(rs.getLong("todo_id"));
                                                            double distance =
                                                                    rs.getDouble("distance");
                                                            if (!Double.isFinite(distance))
                                                                throw new AiProviderException(
                                                                        TimePredictionResult
                                                                                .ClaudeStatus
                                                                                .INVALID_RESPONSE);
                                                            return new SimilarTodoSummary(
                                                                    r.todoId(),
                                                                    r.title(),
                                                                    r.userMinutes(),
                                                                    r.actualSeconds(),
                                                                    distance);
                                                        });
                            }
                            var status =
                                    candidates.isEmpty()
                                            ? VoyageStatus.NO_HISTORY
                                            : partial
                                                    ? VoyageStatus.PARTIAL
                                                    : VoyageStatus.AVAILABLE;
                            result.add(
                                    new SimilarTodoSearchResponse(
                                            status, List.copyOf(items), asOf));
                        }
                        return List.copyOf(result);
                    });
        } catch (AiProviderException ex) {
            return empty(inputs, VoyageStatus.UNAVAILABLE, asOf);
        } catch (org.springframework.dao.DataAccessException ex) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    private Map<Long, String> metadata(Long user) {
        var result = new HashMap<Long, String>();
        jdbc.query(
                "select e.todo_id,e.embedding_model,e.source_text_hash from todo_embeddings e join"
                    + " todos t on t.id=e.todo_id where t.user_id=? and vector_norm(e.embedding)>0",
                (org.springframework.jdbc.core.RowCallbackHandler)
                        rs -> result.put(rs.getLong(1), rs.getString(2) + ":" + rs.getString(3)),
                user);
        return result;
    }

    private boolean matches(Map<Long, String> indexed, AiHistory.Observation r) {
        return (voyage.model() + ":" + r.sourceHash()).equals(indexed.get(r.todoId()));
    }

    private List<SimilarTodoSearchResponse> empty(
            List<SearchInput> inputs, VoyageStatus status, Instant now) {
        return inputs.stream()
                .map(i -> new SimilarTodoSearchResponse(status, List.of(), now))
                .toList();
    }

    private String vector(float[] values) {
        return Arrays.toString(values);
    }

    public static void validate(float[] values) {
        if (values == null || values.length != 1024)
            throw new AiProviderException(TimePredictionResult.ClaudeStatus.INVALID_RESPONSE);
        double norm = 0;
        for (float v : values) {
            if (!Float.isFinite(v))
                throw new AiProviderException(TimePredictionResult.ClaudeStatus.INVALID_RESPONSE);
            norm += (double) v * v;
        }
        if (norm == 0)
            throw new AiProviderException(TimePredictionResult.ClaudeStatus.INVALID_RESPONSE);
    }
}
