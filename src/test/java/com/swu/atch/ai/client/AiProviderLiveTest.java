package com.swu.atch.ai.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.swu.atch.ai.dto.request.*;
import com.swu.atch.ai.dto.response.*;
import com.swu.atch.ai.service.*;
import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.session.domain.*;
import com.swu.atch.session.repository.SessionRepository;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import com.swu.atch.todo.domain.*;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.json.JsonMapper;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

// 별도 환경변수와 명시적 실행으로만 호출한다. 영속 원장으로 재실행을 포함해 합계10회를 제한한다.
@EnabledIfEnvironmentVariable(named = "ATCH_AI_LIVE", matches = "true")
class AiProviderLiveTest extends IsolatedAuthIntegrationTest {
    @MockitoBean ClaudeGateway claude;
    @MockitoBean VoyageGateway voyage;
    @Autowired AiJson json;
    @Autowired JsonMapper mapper;
    @Autowired AiDraftService drafts;
    @Autowired TaskDecompositionService decompositions;
    @Autowired TimePredictionService predictions;
    @Autowired AiTodoService aiTodos;
    @Autowired UserRepository users;
    @Autowired AuthAccountRepository accounts;
    @Autowired AccountCredentials credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired TodoRepository todos;
    @Autowired SessionRepository sessions;
    private static final Path LEDGER =
            Path.of("build/verification/ai-20261029/live-call-ledger.json");

    private record Call(
            int number, String provider, String purpose, Instant startedAt, String status) {}

    private synchronized <T> T call(String provider, String purpose, Supplier<T> work)
            throws Exception {
        Files.createDirectories(LEDGER.getParent());
        var list = new ArrayList<Call>();
        if (Files.exists(LEDGER))
            list.addAll(Arrays.asList(mapper.readValue(Files.readString(LEDGER), Call[].class)));
        if (list.size() >= 10) throw new IllegalStateException("승인된 실호출 합계10회에 도달했습니다.");
        int index = list.size();
        Instant now = Instant.now();
        list.add(new Call(index + 1, provider, purpose, now, "STARTED"));
        Files.writeString(LEDGER, mapper.writeValueAsString(list));
        try {
            T value = work.get();
            list.set(index, new Call(index + 1, provider, purpose, now, "SUCCESS"));
            return value;
        } catch (RuntimeException ex) {
            list.set(index, new Call(index + 1, provider, purpose, now, "FAILED"));
            throw ex;
        } finally {
            Files.writeString(LEDGER, mapper.writeValueAsString(list));
        }
    }

    @Test
    void verifiesSyntheticDecompositionRetrievalPredictionAndCachedConfirmation() throws Exception {
        String key = Objects.requireNonNull(System.getenv("ANTHROPIC_API_KEY"));
        String voyageKey = Objects.requireNonNull(System.getenv("VOYAGE_API_KEY"));
        String model = System.getenv().getOrDefault("ANTHROPIC_MODEL", "claude-haiku-5-5");
        AnthropicClient client =
                AnthropicOkHttpClient.builder()
                        .apiKey(key)
                        .timeout(Duration.ofSeconds(30))
                        .maxRetries(0)
                        .build();
        try {
            var realClaude =
                    new StructuredClaudeGateway(
                            new StaticListableBeanFactory(Map.of("client", client))
                                    .getBeanProvider(AnthropicClient.class),
                            true,
                            model,
                            json,
                            mapper);
            var realVoyage =
                    new VoyageApiClient(
                            true, voyageKey, "voyage-4-lite", Duration.ofSeconds(10), mapper);
            when(claude.enabled()).thenReturn(true);
            when(claude.model()).thenReturn(model);
            when(voyage.enabled()).thenReturn(true);
            when(voyage.model()).thenReturn(realVoyage.model());
            when(claude.decompose(any()))
                    .thenAnswer(
                            inv ->
                                    call(
                                            "Claude",
                                            "decomposition",
                                            () -> realClaude.decompose(inv.getArgument(0))));
            when(claude.predict(anyList()))
                    .thenAnswer(
                            inv ->
                                    call(
                                            "Claude",
                                            "batch-prediction",
                                            () -> realClaude.predict(inv.getArgument(0))));
            when(voyage.embed(anyList(), anyString()))
                    .thenAnswer(
                            inv ->
                                    call(
                                            "Voyage",
                                            "embedding-" + inv.<String>getArgument(1),
                                            () ->
                                                    realVoyage.embed(
                                                            inv.getArgument(0),
                                                            inv.getArgument(1))));
            var owner = users.saveAndFlush(new User("통합검증"));
            var account =
                    accounts.saveAndFlush(
                            AuthAccount.local(
                                    owner,
                                    UUID.randomUUID() + "@atch.invalid",
                                    encoder.encode("Synthetic123!")));
            String stamp = credentials.stamp(account);
            Instant before = Instant.now().minusSeconds(60);
            var history =
                    Todo.builder()
                            .user(owner)
                            .title("합성 보고서 초안 작성")
                            .memo("실호출 검증 전용 합성 자료")
                            .startAt(before.minusSeconds(3600))
                            .dueAt(before)
                            .currentScheduleDate(LocalDate.now(ZoneId.of("Asia/Seoul")))
                            .allDay(false)
                            .userEstimatedDurationMinutes(10)
                            .userEstimateSource(UserEstimateSource.USER_INPUT)
                            .selectedEstimateType(SelectedEstimateType.USER)
                            .build();
            history.complete(before);
            todos.saveAndFlush(history);
            var session = Session.start(history, before.minusSeconds(900));
            session.finish(before, SessionEndReason.COMPLETED);
            sessions.saveAndFlush(session);
            var root = drafts.issue(owner.getId(), stamp, new AiDraftRequest(null, null));
            LocalDate day = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);
            var request =
                    new TaskDecompositionRequest(
                            root.draftId(),
                            "합성 검증 보고서 작성",
                            "자료 수집과 초안 작성으로 나누는 테스트 작업",
                            day,
                            LocalTime.of(9, 0),
                            day,
                            LocalTime.of(11, 0),
                            false,
                            "실제 사용자 자료가 없는 검증용 작업");
            var decomposition = decompositions.decompose(owner.getId(), stamp, request);
            assertThat(decomposition.steps()).isNotEmpty();
            assertThat(decompositions.decompose(owner.getId(), stamp, request).cached()).isTrue();
            var tasks =
                    decomposition.steps().stream()
                            .map(s -> new DecomposedTaskInput(s.draftId(), s.title(), 10))
                            .toList();
            var batch =
                    new TimePredictionBatchRequest(
                            root.draftId(), request.title(), request.memo(), tasks);
            var result = predictions.batch(owner.getId(), stamp, batch);
            assertThat(result.predictions())
                    .allSatisfy(
                            p -> {
                                assertThat(p.claudeStatus())
                                        .isEqualTo(TimePredictionResult.ClaudeStatus.APPLIED);
                                assertThat(p.voyageStatus())
                                        .isEqualTo(
                                                SimilarTodoSearchResponse.VoyageStatus.AVAILABLE);
                                assertThat(p.similarTodos()).hasSize(1);
                            });
            var cached = predictions.batch(owner.getId(), stamp, batch);
            assertThat(cached.predictions()).allSatisfy(p -> assertThat(p.cached()).isTrue());
            var confirmation =
                    new DecomposedTodoCreateRequest(
                            UUID.randomUUID(),
                            root.draftId(),
                            request.title(),
                            request.memo(),
                            day,
                            request.startTime(),
                            day,
                            request.dueTime(),
                            false,
                            tasks,
                            new com.swu.atch.todo.dto.request.EstimateChoiceRequest(
                                    root.draftId(),
                                    SelectedEstimateType.AI,
                                    cached.predictionToken()));
            var saved = aiTodos.confirm(owner.getId(), stamp, confirmation);
            assertThat(saved.items()).hasSize(tasks.size());
            assertThat(aiTodos.confirm(owner.getId(), stamp, confirmation).replayed()).isTrue();
            verify(claude, times(1)).decompose(any());
            verify(claude, times(1)).predict(anyList());
            verify(voyage, times(1)).embed(anyList(), eq("document"));
            verify(voyage, times(1)).embed(anyList(), eq("query"));
            Files.writeString(
                    LEDGER.resolveSibling("live-result.json"),
                    mapper.writeValueAsString(
                            Map.of(
                                    "verifiedAt",
                                    Instant.now(),
                                    "model",
                                    model,
                                    "embeddingModel",
                                    realVoyage.model(),
                                    "tasks",
                                    tasks.size(),
                                    "userTotal",
                                    saved.totalUserEstimatedDurationMinutes(),
                                    "aiTotal",
                                    saved.totalAiEstimatedDurationMinutes(),
                                    "cacheReplay",
                                    true,
                                    "creationReplay",
                                    true,
                                    "syntheticOnly",
                                    true)));
        } finally {
            client.close();
        }
    }
}
