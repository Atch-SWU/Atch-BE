package com.swu.atch.ai.service;

import com.swu.atch.ai.client.*;
import com.swu.atch.ai.domain.*;
import com.swu.atch.ai.dto.request.TaskDecompositionRequest;
import com.swu.atch.ai.dto.response.*;
import com.swu.atch.ai.repository.AiDraftRepository;
import com.swu.atch.common.exception.*;
import com.swu.atch.todo.service.TodoSchedulePolicy;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TaskDecompositionService {
    private final AiTransactions tx;
    private final AiDraftService drafts;
    private final AiDraftRepository repository;
    private final AiOperationLedger ledger;
    private final AiRateLimiter limits;
    private final ClaudeGateway claude;
    private final TodoSchedulePolicy schedule;
    private final AiJson json;

    public TaskDecompositionResponse decompose(
            Long user, String stamp, TaskDecompositionRequest input) {
        var normalized =
                new TaskDecompositionRequest(
                        input.draftId(),
                        AiJson.title(input.title()),
                        input.taskDescription(),
                        input.startDate(),
                        input.startTime(),
                        input.dueDate(),
                        input.dueTime(),
                        input.allDay(),
                        input.memo());
        var ticket =
                tx.locked(
                        user,
                        stamp,
                        owner -> {
                            Instant now = Instant.now();
                            var draft = drafts.owned(input.draftId(), user);
                            String hash = json.hash(normalized);
                            if (draft.getTodo() != null || draft.getParent() != null)
                                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT);
                            ledger.check(draft, AiFeature.DECOMPOSITION, hash, now);
                            if (!ledger.cached(draft, AiFeature.DECOMPOSITION)) {
                                schedule.requireNewStartDate(input.startDate(), now);
                                schedule.schedule(
                                        input.startDate(),
                                        input.startTime(),
                                        input.dueDate(),
                                        input.dueTime(),
                                        input.allDay());
                                limits.acquire(user, false, 1);
                            }
                            return ledger.reserve(draft, AiFeature.DECOMPOSITION, hash, now);
                        });
        if (ticket.cached()) {
            var cached = json.read(ticket.cachedPayload(), TaskDecompositionResponse.class);
            return new TaskDecompositionResponse(
                    cached.decompositionDraftId(),
                    cached.sourceTaskName(),
                    cached.steps(),
                    cached.usage(),
                    true,
                    cached.expiresAt());
        }
        try {
            if (!claude.enabled())
                throw new AiProviderException(TimePredictionResult.ClaudeStatus.DISABLED);
            var titles = claude.decompose(normalized);
            if (titles == null
                    || titles.isEmpty()
                    || titles.size() > 20
                    || titles.stream().anyMatch(t -> t == null || t.isBlank() || t.length() > 200))
                throw new AiProviderException(TimePredictionResult.ClaudeStatus.INVALID_RESPONSE);
            return tx.locked(
                    user,
                    stamp,
                    owner -> {
                        var draft = drafts.owned(input.draftId(), user);
                        var steps = new ArrayList<TaskDecompositionResponse.Step>();
                        // 외부 호출 중 일반 Todo로 확정했으면 세부 항목을 새로 만들지 않는다.
                        if (draft.getFinalizedAt() != null || draft.getTodo() != null)
                            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT);
                        int position = 1;
                        for (String title : titles) {
                            var child =
                                    repository.saveAndFlush(
                                            new AiDraft(owner, null, draft, Instant.now()));
                            steps.add(
                                    new TaskDecompositionResponse.Step(
                                            child.getDraftId(), position++, title.trim()));
                        }
                        var response =
                                new TaskDecompositionResponse(
                                        draft.getDraftId(),
                                        normalized.title(),
                                        List.copyOf(steps),
                                        new AiUsageResponse(
                                                AiUsageResponse.State.SUCCEEDED,
                                                false,
                                                Instant.now(),
                                                true),
                                        false,
                                        draft.getExpiresAt());
                        ledger.succeed(
                                user,
                                ticket,
                                AiFeature.DECOMPOSITION,
                                json.write(response),
                                claude.model());
                        return response;
                    });
        } catch (RuntimeException ex) {
            try {
                tx.locked(
                        user,
                        stamp,
                        owner -> {
                            ledger.fail(
                                    user,
                                    ticket,
                                    AiFeature.DECOMPOSITION,
                                    ex instanceof AiProviderException p
                                            ? p.status().name()
                                            : "REQUEST_FAILED");
                            return null;
                        });
            } catch (RuntimeException ignored) {
                /* 저장소 장애는 예약 만료 후 회수한다. */
            }
            if (ex instanceof AiProviderException p)
                throw new BusinessException(
                        switch (p.status()) {
                            case DISABLED -> ErrorCode.AI_FEATURE_DISABLED;
                            case TIMEOUT -> ErrorCode.AI_DECOMPOSITION_TIMEOUT;
                            default -> ErrorCode.AI_DECOMPOSITION_FAILED;
                        });
            throw ex;
        }
    }
}
