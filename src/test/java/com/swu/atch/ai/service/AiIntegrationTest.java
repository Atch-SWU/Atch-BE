package com.swu.atch.ai.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.swu.atch.ai.client.*;
import com.swu.atch.ai.domain.*;
import com.swu.atch.ai.dto.request.*;
import com.swu.atch.ai.dto.response.*;
import com.swu.atch.ai.repository.*;
import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.jwt.JwtTokenService;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.*;
import com.swu.atch.session.domain.*;
import com.swu.atch.session.repository.SessionRepository;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import com.swu.atch.todo.domain.*;
import com.swu.atch.todo.dto.request.*;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.todo.service.TodoService;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import com.swu.atch.user.service.UserAccountService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

// PostgreSQL·Redis·JWT는 실제 폐기 자원, Claude·Voyage만 대체한다.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AiIntegrationTest extends IsolatedAuthIntegrationTest {
    @MockitoBean ClaudeGateway claude;
    @MockitoBean VoyageGateway voyage;
    @Autowired AiDraftService drafts;
    @Autowired TimePredictionService predictions;
    @Autowired TaskDecompositionService decompositions;
    @Autowired AiTodoService aiTodos;
    @Autowired TodoService todoService;
    @Autowired ProfileService profiles;
    @Autowired SimilarTodoService similar;
    @Autowired AiTransactions tx;
    @Autowired AiJson json;
    @Autowired AiRateLimiter limits;
    @Autowired PredictionTokenService offerTokens;
    @Autowired AiDraftRepository draftRepository;
    @Autowired AiOperationRepository operationRepository;
    @Autowired TodoRepository todos;
    @Autowired UserRepository users;
    @Autowired AuthAccountRepository accounts;
    @Autowired AccountCredentials credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenService jwt;
    @Autowired SessionRepository sessions;
    @Autowired UserAccountService accountService;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    private record Actor(long id, String stamp, String access) {}

    private static final String PASSWORD = "AiTests123!";

    private static LocalDate day() {
        return LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);
    }

    @BeforeEach
    void providers() {
        when(claude.enabled()).thenReturn(true);
        when(claude.model()).thenReturn("fixture-claude");
        when(voyage.enabled()).thenReturn(false);
        when(voyage.model()).thenReturn("voyage-4-lite");
        when(claude.predict(anyList()))
                .thenAnswer(
                        invocation -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            List<ClaudeGateway.Context> inputs = invocation.getArgument(0);
                            var values = new HashMap<UUID, ClaudeGateway.Advice>();
                            inputs.forEach(
                                    i ->
                                            values.put(
                                                    i.draftId(),
                                                    new ClaudeGateway.Advice(
                                                            1.2,
                                                            List.of(
                                                                    PredictionReasonCode
                                                                            .CONTEXT_ADJUSTED_UP))));
                            return values;
                        });
        when(claude.decompose(any()))
                .thenAnswer(
                        invocation -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return List.of("자료 수집", "초안 작성");
                        });
    }

    private Actor actor() {
        var user = users.saveAndFlush(new User("인공지능"));
        var account =
                accounts.saveAndFlush(
                        AuthAccount.local(
                                user,
                                UUID.randomUUID() + "@atch.invalid",
                                encoder.encode(PASSWORD)));
        String stamp = credentials.stamp(account);
        return new Actor(user.getId(), stamp, jwt.issueAccessToken(user.getId(), stamp));
    }

    private UUID draft(Actor a) {
        return drafts.issue(a.id, a.stamp, new AiDraftRequest(null, null)).draftId();
    }

    private TimePredictionRequest request(UUID id) {
        return new TimePredictionRequest(id, "기획안", null, null, 10);
    }

    private TaskDecompositionRequest decomposition(UUID id) {
        return new TaskDecompositionRequest(
                id,
                "기획안",
                null,
                day(),
                LocalTime.of(9, 0),
                day(),
                LocalTime.of(10, 0),
                false,
                null);
    }

    private TodoCreateRequest createInput(
            UUID id, String token, SelectedEstimateType type, UUID requestId) {
        return new TodoCreateRequest(
                "기획안",
                null,
                day(),
                LocalTime.of(9, 0),
                day(),
                LocalTime.of(10, 0),
                false,
                10,
                new EstimateChoiceRequest(id, type, token),
                requestId);
    }

    private DecomposedTodoCreateRequest confirmInput(
            TaskDecompositionResponse d,
            UUID requestId,
            String token,
            SelectedEstimateType type,
            List<DecomposedTaskInput> tasks) {
        return new DecomposedTodoCreateRequest(
                requestId,
                d.decompositionDraftId(),
                "기획안",
                null,
                day(),
                LocalTime.of(9, 0),
                day(),
                LocalTime.of(10, 0),
                false,
                tasks,
                new EstimateChoiceRequest(d.decompositionDraftId(), type, token));
    }

    private List<DecomposedTaskInput> tasks(TaskDecompositionResponse d) {
        return d.steps().stream()
                .map(t -> new DecomposedTaskInput(t.draftId(), t.title(), 10))
                .toList();
    }

    private TimePredictionBatchRequest batchInput(
            TaskDecompositionResponse d, List<DecomposedTaskInput> items) {
        return new TimePredictionBatchRequest(d.decompositionDraftId(), "기획안", null, items);
    }

    private void code(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private Todo historical(Actor a, int userMinutes, int seconds) {
        Instant now = Instant.now().minusSeconds(60);
        var todo =
                Todo.builder()
                        .user(users.findById(a.id).orElseThrow())
                        .title("과거 작업")
                        .startAt(now.minusSeconds(7200))
                        .dueAt(now.minusSeconds(1))
                        .currentScheduleDate(LocalDate.now(ZoneId.of("Asia/Seoul")))
                        .allDay(false)
                        .userEstimatedDurationMinutes(userMinutes)
                        .userEstimateSource(UserEstimateSource.USER_INPUT)
                        .selectedEstimateType(SelectedEstimateType.USER)
                        .build();
        todo.complete(now);
        todos.saveAndFlush(todo);
        var session = Session.start(todo, now.minusSeconds(seconds));
        session.finish(now, SessionEndReason.COMPLETED);
        sessions.saveAndFlush(session);
        return todo;
    }

    @Test
    void recalculatesValidRawSecondsAndRemovesDeletedObservations() {
        var a = actor();
        assertThat(profiles.get(a.id, a.stamp).state())
                .isEqualTo(TimeEstimationProfileResponse.State.NO_HISTORY);
        var first = historical(a, 1, 1);
        historical(a, 10, 1200);
        assertThat(profiles.get(a.id, a.stamp).state())
                .isEqualTo(TimeEstimationProfileResponse.State.NOT_CALCULATED);
        var p = profiles.recalculate(a.id, a.stamp);
        assertThat(p.sampleCount()).isEqualTo(2);
        assertThat(p.calculatedAsOf()).isNotNull();
        jdbc.update("update todos set deleted_at=now() where id=?", first.getId());
        assertThat(profiles.recalculate(a.id, a.stamp).sampleCount()).isEqualTo(1);
        assertThat(profiles.get(a.id, a.stamp).calculatedAsOf()).isNull();
    }

    @Test
    void excludesOpenFutureRecurrenceAndReopenedRecords() {
        var a = actor();
        var open = historical(a, 10, 600);
        sessions.saveAndFlush(Session.start(open, Instant.now().minusSeconds(1)));
        var future = historical(a, 10, 600);
        jdbc.update(
                "update todos set completed_at=now()+interval '1 day' where id=?", future.getId());
        var template = historical(a, 10, 600);
        jdbc.update(
                "update todos set user_estimate_source='RECURRENCE_TEMPLATE' where id=?",
                template.getId());
        var reopened = historical(a, 10, 600);
        jdbc.update(
                "update todos set status='PENDING',completed_at=null where id=?", reopened.getId());
        assertThat(profiles.recalculate(a.id, a.stamp).sampleCount()).isZero();
    }

    @Test
    void cachesOneSuccessAcrossFreshServiceCallsAndRejectsChangedInputs() {
        var a = actor();
        UUID id = draft(a);
        var first = predictions.predict(a.id, a.stamp, request(id));
        var second = predictions.predict(a.id, a.stamp, request(id));
        assertThat(first.prediction().aiEstimatedDurationMinutes()).isEqualTo(12);
        assertThat(second.prediction().cached()).isTrue();
        assertThat(second.asOf()).isEqualTo(first.asOf());
        verify(claude, times(1)).predict(anyList());
        code(
                () ->
                        predictions.predict(
                                a.id, a.stamp, new TimePredictionRequest(id, "변경", null, null, 10)),
                ErrorCode.AI_ALREADY_USED);
        assertThat(
                        jdbc.queryForObject(
                                "select o.attempt_count from ai_operations o join ai_drafts d on"
                                    + " d.id=o.draft_id where d.draft_id=?",
                                Integer.class,
                                id))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(
            value = TimePredictionResult.ClaudeStatus.class,
            names = {
                "TIMEOUT",
                "RATE_LIMITED",
                "REFUSED",
                "TRUNCATED",
                "INVALID_RESPONSE",
                "UNAVAILABLE"
            })
    void failuresFallbackAndAllowExplicitRetry(TimePredictionResult.ClaudeStatus status) {
        var a = actor();
        UUID id = draft(a);
        when(claude.predict(anyList())).thenThrow(new AiProviderException(status));
        var failed = predictions.predict(a.id, a.stamp, request(id));
        assertThat(failed.prediction().mode())
                .isEqualTo(TimePredictionResult.Mode.STATISTICAL_BASELINE);
        assertThat(failed.prediction().aiEstimatedDurationMinutes()).isEqualTo(10);
        assertThat(failed.prediction().usage().retryAllowed()).isTrue();
        doReturn(
                        Map.of(
                                id,
                                new ClaudeGateway.Advice(
                                        1, List.of(PredictionReasonCode.BASELINE_RETAINED))))
                .when(claude)
                .predict(anyList());
        assertThat(predictions.predict(a.id, a.stamp, request(id)).prediction().usage().state())
                .isEqualTo(AiUsageResponse.State.SUCCEEDED);
    }

    @Test
    void disabledClaudeStillAllowsOrdinaryTodoCreation() {
        var a = actor();
        when(claude.enabled()).thenReturn(false);
        UUID id = draft(a);
        assertThat(predictions.predict(a.id, a.stamp, request(id)).prediction().claudeStatus())
                .isEqualTo(TimePredictionResult.ClaudeStatus.DISABLED);
        var created =
                todoService.create(
                        a.id,
                        a.stamp,
                        new TodoCreateRequest(
                                "일반 생성",
                                null,
                                day(),
                                LocalTime.of(9, 0),
                                day(),
                                LocalTime.of(10, 0),
                                false,
                                10));
        assertThat(created.selectedEstimateType()).isEqualTo(SelectedEstimateType.USER);
    }

    @Test
    void createsSingleAndReplaysAfterExpiryWithoutCreatingAgain() {
        var a = actor();
        UUID id = draft(a);
        var prediction = predictions.predict(a.id, a.stamp, request(id));
        UUID key = UUID.randomUUID();
        var input = createInput(id, prediction.predictionToken(), SelectedEstimateType.AI, key);
        var first = aiTodos.create(a.id, a.stamp, input);
        assertThat(first.todo().userEstimatedDurationMinutes()).isEqualTo(10);
        assertThat(first.todo().aiEstimatedDurationMinutes()).isEqualTo(12);
        jdbc.update("update ai_drafts set expires_at=now()-interval '1 day' where draft_id=?", id);
        var replay = aiTodos.create(a.id, a.stamp, input);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.todo()).isEqualTo(first.todo());
        code(
                () ->
                        aiTodos.create(
                                a.id,
                                a.stamp,
                                createInput(
                                        id,
                                        prediction.predictionToken(),
                                        SelectedEstimateType.AI,
                                        UUID.randomUUID())),
                ErrorCode.RESOURCE_CONFLICT);
        var resumed = drafts.issue(a.id, a.stamp, new AiDraftRequest(first.todo().id(), null));
        assertThat(resumed.draftId()).isEqualTo(id);
        assertThat(resumed.timePredictionUsage().state())
                .isEqualTo(AiUsageResponse.State.SUCCEEDED);
    }

    @Test
    void rejectsWrongOwnerTamperingInputAndAccessTokens() {
        var a = actor();
        var b = actor();
        UUID id = draft(a);
        var p = predictions.predict(a.id, a.stamp, request(id));
        code(() -> predictions.predict(b.id, b.stamp, request(id)), ErrorCode.RESOURCE_NOT_FOUND);
        code(
                () ->
                        aiTodos.create(
                                a.id,
                                a.stamp,
                                createInput(
                                        id, a.access, SelectedEstimateType.AI, UUID.randomUUID())),
                ErrorCode.AI_OFFER_INVALID);
        code(
                () ->
                        aiTodos.create(
                                a.id,
                                a.stamp,
                                createInput(
                                        id,
                                        p.predictionToken() + "x",
                                        SelectedEstimateType.AI,
                                        UUID.randomUUID())),
                ErrorCode.AI_OFFER_INVALID);
        var wrong =
                new TodoCreateRequest(
                        "다른 입력",
                        null,
                        day(),
                        LocalTime.of(9, 0),
                        day(),
                        LocalTime.of(10, 0),
                        false,
                        10,
                        new EstimateChoiceRequest(id, SelectedEstimateType.AI, p.predictionToken()),
                        UUID.randomUUID());
        code(() -> aiTodos.create(a.id, a.stamp, wrong), ErrorCode.AI_OFFER_INVALID);
    }

    @Test
    void userRejectionPreservesOfferAndOriginalInput() {
        var a = actor();
        UUID id = draft(a);
        var p = predictions.predict(a.id, a.stamp, request(id));
        var saved =
                aiTodos.create(
                        a.id,
                        a.stamp,
                        createInput(
                                id,
                                p.predictionToken(),
                                SelectedEstimateType.USER,
                                UUID.randomUUID()));
        assertThat(saved.todo().estimatedDurationMinutes()).isEqualTo(10);
        assertThat(saved.todo().aiEstimatedDurationMinutes()).isEqualTo(12);
    }

    @Test
    void existingTodoPredictionAppliesAndOrdinaryEditingDoesNotResetUsage() {
        var a = actor();
        var original =
                todoService.create(
                        a.id,
                        a.stamp,
                        new TodoCreateRequest(
                                "기획안",
                                null,
                                day(),
                                LocalTime.of(9, 0),
                                day(),
                                LocalTime.of(10, 0),
                                false,
                                10));
        var id = drafts.issue(a.id, a.stamp, new AiDraftRequest(original.id(), null)).draftId();
        var p = predictions.predict(a.id, a.stamp, request(id));
        var updated =
                todoService.update(
                        a.id,
                        a.stamp,
                        original.id(),
                        new TodoUpdateRequest(
                                "기획안",
                                null,
                                day(),
                                LocalTime.of(9, 0),
                                day(),
                                LocalTime.of(10, 0),
                                false,
                                10,
                                new EstimateChoiceRequest(
                                        id, SelectedEstimateType.AI, p.predictionToken())));
        assertThat(updated.aiEstimatedDurationMinutes()).isEqualTo(12);
        var changed =
                todoService.update(
                        a.id,
                        a.stamp,
                        original.id(),
                        new TodoUpdateRequest(
                                "변경",
                                null,
                                day(),
                                LocalTime.of(9, 0),
                                day(),
                                LocalTime.of(10, 0),
                                false,
                                10));
        assertThat(changed.aiEstimatedDurationMinutes()).isNull();
        code(
                () ->
                        predictions.predict(
                                a.id, a.stamp, new TimePredictionRequest(id, "변경", null, null, 10)),
                ErrorCode.AI_ALREADY_USED);
    }

    @Test
    void successfulDecompositionReplaysWithoutNewChildrenAndAllowsManualChild() {
        var a = actor();
        UUID id = draft(a);
        var first = decompositions.decompose(a.id, a.stamp, decomposition(id));
        var again = decompositions.decompose(a.id, a.stamp, decomposition(id));
        assertThat(again.cached()).isTrue();
        assertThat(again.steps()).isEqualTo(first.steps());
        verify(claude, times(1)).decompose(any());
        var child = drafts.issue(a.id, a.stamp, new AiDraftRequest(null, id));
        assertThat(child.decompositionUsage().state())
                .isEqualTo(AiUsageResponse.State.NOT_APPLICABLE);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from todos where user_id=?", Long.class, a.id))
                .isZero();
    }

    @Test
    void batchRetriesOnlyFailedItemsAndRetainsOldAsOf() {
        var a = actor();
        var d = decompositions.decompose(a.id, a.stamp, decomposition(draft(a)));
        var items = tasks(d);
        UUID good = items.getFirst().draftId();
        when(claude.predict(anyList()))
                .thenReturn(
                        Map.of(
                                good,
                                new ClaudeGateway.Advice(
                                        1.1, List.of(PredictionReasonCode.BASELINE_RETAINED))));
        var first = predictions.batch(a.id, a.stamp, batchInput(d, items));
        assertThat(first.predictions().get(1).usage().retryAllowed()).isTrue();
        when(claude.predict(anyList()))
                .thenAnswer(
                        inv -> {
                            List<ClaudeGateway.Context> submitted = inv.getArgument(0);
                            assertThat(submitted).hasSize(1);
                            assertThat(submitted.getFirst().draftId())
                                    .isEqualTo(items.get(1).draftId());
                            return Map.of(
                                    items.get(1).draftId(),
                                    new ClaudeGateway.Advice(
                                            1.2, List.of(PredictionReasonCode.BASELINE_RETAINED)));
                        });
        var retried = predictions.batch(a.id, a.stamp, batchInput(d, items));
        assertThat(retried.predictions().getFirst().cached()).isTrue();
        assertThat(retried.predictions().getFirst().asOf())
                .isEqualTo(first.predictions().getFirst().asOf());
    }

    @Test
    void savesApprovedChildrenAtomicallyAndReplaysInSubmittedOrder() {
        var a = actor();
        var d = decompositions.decompose(a.id, a.stamp, decomposition(draft(a)));
        var items = tasks(d);
        var p = predictions.batch(a.id, a.stamp, batchInput(d, items));
        var input =
                confirmInput(
                        d, UUID.randomUUID(), p.predictionToken(), SelectedEstimateType.AI, items);
        var result = aiTodos.confirm(a.id, a.stamp, input);
        assertThat(result.totalUserEstimatedDurationMinutes()).isEqualTo(20);
        assertThat(result.totalAiEstimatedDurationMinutes()).isEqualTo(24);
        assertThat(result.items()).hasSize(2);
        assertThat(result.items()).extracting(i -> i.todo().sourceTaskName()).containsOnly("기획안");
        assertThat(aiTodos.confirm(a.id, a.stamp, input).replayed()).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from todos where user_id=?", Long.class, a.id))
                .isEqualTo(2);
        code(
                () ->
                        aiTodos.confirm(
                                a.id,
                                a.stamp,
                                confirmInput(
                                        d,
                                        UUID.randomUUID(),
                                        null,
                                        SelectedEstimateType.USER,
                                        items)),
                ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    void rejectsForeignOrDuplicateChildrenBeforeAnyInsert() {
        var a = actor();
        var b = actor();
        var d = decompositions.decompose(a.id, a.stamp, decomposition(draft(a)));
        var foreign = draft(b);
        code(
                () ->
                        aiTodos.confirm(
                                a.id,
                                a.stamp,
                                confirmInput(
                                        d,
                                        UUID.randomUUID(),
                                        null,
                                        SelectedEstimateType.USER,
                                        List.of(
                                                tasks(d).getFirst(),
                                                new DecomposedTaskInput(foreign, "타인", 10)))),
                ErrorCode.RESOURCE_NOT_FOUND);
        code(
                () ->
                        aiTodos.confirm(
                                a.id,
                                a.stamp,
                                confirmInput(
                                        d,
                                        UUID.randomUUID(),
                                        null,
                                        SelectedEstimateType.USER,
                                        List.of(tasks(d).getFirst(), tasks(d).getFirst()))),
                ErrorCode.INVALID_REQUEST);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from todos where user_id=?", Long.class, a.id))
                .isZero();
    }

    @Test
    void rollsBackEarlierInsertsWhenLaterDatabaseWriteFails() {
        var a = actor();
        var d = decompositions.decompose(a.id, a.stamp, decomposition(draft(a)));
        jdbc.execute(
                "create function ai_test_fail_second() returns trigger language plpgsql as $$ begin"
                    + " if NEW.title='실패 전용' then raise exception 'synthetic failure'; end if;"
                    + " return NEW; end $$");
        jdbc.execute(
                "create trigger ai_test_fail_second before insert on todos for each row execute"
                    + " function ai_test_fail_second()");
        try {
            var items =
                    List.of(
                            tasks(d).getFirst(),
                            new DecomposedTaskInput(d.steps().get(1).draftId(), "실패 전용", 10));
            code(
                    () ->
                            aiTodos.confirm(
                                    a.id,
                                    a.stamp,
                                    confirmInput(
                                            d,
                                            UUID.randomUUID(),
                                            null,
                                            SelectedEstimateType.USER,
                                            items)),
                    ErrorCode.AI_SERVICE_UNAVAILABLE);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from todos where user_id=?", Long.class, a.id))
                    .isZero();
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from ai_creation_requests where user_id=?",
                                    Long.class,
                                    a.id))
                    .isZero();
        } finally {
            jdbc.execute("drop trigger ai_test_fail_second on todos");
            jdbc.execute("drop function ai_test_fail_second()");
        }
    }

    @Test
    void concurrentPredictionClaimsOneProviderCallAndLateRetryUsesCache() throws Exception {
        var a = actor();
        UUID id = draft(a);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(claude.predict(anyList()))
                .thenAnswer(
                        inv -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            started.countDown();
                            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                            return Map.of(
                                    id,
                                    new ClaudeGateway.Advice(
                                            1, List.of(PredictionReasonCode.BASELINE_RETAINED)));
                        });
        var pool = Executors.newSingleThreadExecutor();
        try {
            var running = pool.submit(() -> predictions.predict(a.id, a.stamp, request(id)));
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            code(() -> predictions.predict(a.id, a.stamp, request(id)), ErrorCode.AI_IN_PROGRESS);
            release.countDown();
            running.get(10, TimeUnit.SECONDS);
            assertThat(predictions.predict(a.id, a.stamp, request(id)).prediction().cached())
                    .isTrue();
            verify(claude, times(1)).predict(anyList());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void preservesUnknownLegacyHistoryAndDeletesAllLedgersOnWithdrawal() {
        var a = actor();
        UUID id = draft(a);
        var p = predictions.predict(a.id, a.stamp, request(id));
        var created =
                aiTodos.create(
                        a.id,
                        a.stamp,
                        createInput(
                                id,
                                p.predictionToken(),
                                SelectedEstimateType.AI,
                                UUID.randomUUID()));
        // 출처 이력 없는 기존 값은 성공이었다고 추측하지 않는다.
        var legacy =
                todoService.create(
                        a.id,
                        a.stamp,
                        new TodoCreateRequest(
                                "기존 작업",
                                null,
                                day(),
                                LocalTime.of(9, 0),
                                day(),
                                LocalTime.of(10, 0),
                                false,
                                10));
        jdbc.update("update todos set ai_estimated_duration_minutes=20 where id=?", legacy.id());
        var legacyDraft = drafts.issue(a.id, a.stamp, new AiDraftRequest(legacy.id(), null));
        assertThat(legacyDraft.timePredictionUsage().state())
                .isEqualTo(AiUsageResponse.State.LEGACY_UNKNOWN);
        code(
                () ->
                        predictions.predict(
                                a.id,
                                a.stamp,
                                new TimePredictionRequest(
                                        legacyDraft.draftId(), "기존 작업", null, null, 10)),
                ErrorCode.AI_ALREADY_USED);
        accountService.withdraw(a.id, a.stamp, PASSWORD);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from ai_drafts where user_id=?", Long.class, a.id))
                .isZero();
        assertThat(todos.findById(created.todo().id())).isEmpty();
    }

    @Test
    void findsTopThreeOwnFreshEmbeddingsAndRefreshesChangedHash() {
        var a = actor();
        var b = actor();
        for (int i = 0; i < 4; i++) historical(a, 10, 600 + i);
        historical(b, 10, 900);
        when(voyage.enabled()).thenReturn(true);
        when(voyage.embed(anyList(), anyString()))
                .thenAnswer(
                        inv -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            List<String> texts = inv.getArgument(0);
                            return texts.stream()
                                    .map(
                                            t -> {
                                                float[] v = new float[1024];
                                                v[0] = 1;
                                                return v;
                                            })
                                    .toList();
                        });
        var found =
                similar.search(a.id, a.stamp, new SimilarTodoSearchRequest("기획", null, null, null));
        assertThat(found.items()).hasSize(3);
        assertThat(found.voyageStatus())
                .isEqualTo(SimilarTodoSearchResponse.VoyageStatus.AVAILABLE);
        var id = found.items().getFirst().todoId();
        jdbc.update("update todos set memo='변경' where id=?", id);
        similar.search(a.id, a.stamp, new SimilarTodoSearchRequest("기획", null, null, null));
        verify(voyage, times(2)).embed(anyList(), eq("document"));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from todo_embeddings e join todos t on"
                                    + " t.id=e.todo_id where t.user_id=?",
                                Long.class,
                                b.id))
                .isZero();
    }

    @Test
    void voyageFailureDoesNotPretendNoHistoryOrBreakClaude() {
        var a = actor();
        historical(a, 10, 600);
        when(voyage.enabled()).thenReturn(true);
        when(voyage.embed(anyList(), anyString()))
                .thenThrow(new AiProviderException(TimePredictionResult.ClaudeStatus.TIMEOUT));
        var result = predictions.predict(a.id, a.stamp, request(draft(a)));
        assertThat(result.prediction().voyageStatus())
                .isEqualTo(SimilarTodoSearchResponse.VoyageStatus.UNAVAILABLE);
        assertThat(result.prediction().claudeStatus())
                .isEqualTo(TimePredictionResult.ClaudeStatus.APPLIED);
    }

    @Test
    void localLimitIs429WithRetryAfterAndNoProviderRetry() throws Exception {
        var a = actor();
        for (int i = 0; i < 6; i++) limits.acquire(a.id, false, 1);
        UUID id = draft(a);
        var response =
                mvc.perform(
                                post("/api/v1/ai/time-predictions")
                                        .header("Authorization", "Bearer " + a.access)
                                        .contentType("application/json")
                                        .content(json.write(request(id))))
                        .andReturn()
                        .getResponse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        verify(claude, never()).predict(anyList());
    }

    @Test
    void expiredOfferIsRejectedButCanBeReissuedWithoutProviderCall() throws Exception {
        var a = actor();
        UUID id = draft(a);
        var p = predictions.predict(a.id, a.stamp, request(id));
        var parsed = com.nimbusds.jwt.SignedJWT.parse(p.predictionToken());
        var expired =
                new com.nimbusds.jwt.JWTClaimsSet.Builder(parsed.getJWTClaimsSet())
                        .expirationTime(java.util.Date.from(Instant.now().minusSeconds(120)))
                        .build();
        var signed = new com.nimbusds.jwt.SignedJWT(parsed.getHeader(), expired);
        var pair = keyPair();
        signed.sign(
                new com.nimbusds.jose.crypto.RSASSASigner(
                        (java.security.interfaces.RSAPrivateKey) pair.getPrivate()));
        code(
                () ->
                        aiTodos.create(
                                a.id,
                                a.stamp,
                                createInput(
                                        id,
                                        signed.serialize(),
                                        SelectedEstimateType.AI,
                                        UUID.randomUUID())),
                ErrorCode.AI_OFFER_INVALID);
        var renewed = predictions.predict(a.id, a.stamp, request(id));
        assertThat(renewed.prediction().cached()).isTrue();
        assertThat(
                        aiTodos.create(
                                        a.id,
                                        a.stamp,
                                        createInput(
                                                id,
                                                renewed.predictionToken(),
                                                SelectedEstimateType.AI,
                                                UUID.randomUUID()))
                                .todo()
                                .aiEstimatedDurationMinutes())
                .isEqualTo(12);
        verify(claude, times(1)).predict(anyList());
    }

    @Autowired org.springframework.context.ApplicationContext applicationContext;

    private java.security.KeyPair keyPair() {
        return applicationContext.getBean("jwtKeyPair", java.security.KeyPair.class);
    }

    @Test
    void expiredReservationFencesOffLateResponse() {
        var a = actor();
        UUID id = draft(a);
        var ledger = applicationContext.getBean(AiOperationLedger.class);
        var old =
                tx.locked(
                        a.id,
                        a.stamp,
                        owner ->
                                ledger.reserve(
                                        drafts.owned(id, a.id),
                                        AiFeature.TIME_PREDICTION,
                                        "a".repeat(64),
                                        Instant.now()));
        jdbc.update(
                "update ai_operations set lease_until=now()-interval '1 second' where attempt_id=?",
                old.attemptId());
        var fresh =
                tx.locked(
                        a.id,
                        a.stamp,
                        owner ->
                                ledger.reserve(
                                        drafts.owned(id, a.id),
                                        AiFeature.TIME_PREDICTION,
                                        "a".repeat(64),
                                        Instant.now()));
        tx.locked(
                a.id,
                a.stamp,
                owner -> {
                    ledger.succeed(
                            a.id,
                            fresh,
                            AiFeature.TIME_PREDICTION,
                            "{\"fixture\":true}",
                            "fixture");
                    return null;
                });
        code(
                () ->
                        tx.locked(
                                a.id,
                                a.stamp,
                                owner -> {
                                    ledger.succeed(
                                            a.id,
                                            old,
                                            AiFeature.TIME_PREDICTION,
                                            "{\"fixture\":false}",
                                            "fixture");
                                    return null;
                                }),
                ErrorCode.RESOURCE_CONFLICT);
        assertThat(
                        jdbc.queryForObject(
                                "select result_payload->>'fixture' from ai_operations where"
                                    + " attempt_id=?",
                                String.class,
                                fresh.attemptId()))
                .isEqualTo("true");
    }

    @Test
    void databaseRejectsCrossOwnerParentsAndImpossibleSuccessRows() {
        var a = actor();
        var b = actor();
        UUID root = draft(a);
        long parent =
                jdbc.queryForObject("select id from ai_drafts where draft_id=?", Long.class, root);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "insert into"
                                            + " ai_drafts(draft_id,user_id,parent_id,expires_at)"
                                            + " values(?,?,?,now()+interval '1 day')",
                                        UUID.randomUUID(),
                                        b.id,
                                        parent))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "insert into ai_operations(draft_id,feature,state)"
                                            + " values(?,'TIME_PREDICTION','SUCCEEDED')",
                                        parent))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void partialIndexProgressesAndMalformedVectorsAreNotSaved() {
        var a = actor();
        for (int i = 0; i < 17; i++) historical(a, 10, 600 + i);
        when(voyage.enabled()).thenReturn(true);
        when(voyage.embed(anyList(), anyString()))
                .thenAnswer(
                        inv -> {
                            List<String> texts = inv.getArgument(0);
                            return texts.stream()
                                    .map(
                                            t -> {
                                                float[] v = new float[1024];
                                                v[0] = 1;
                                                return v;
                                            })
                                    .toList();
                        });
        var input = new SimilarTodoSearchRequest("기획", null, null, null);
        assertThat(similar.search(a.id, a.stamp, input).voyageStatus())
                .isEqualTo(SimilarTodoSearchResponse.VoyageStatus.PARTIAL);
        assertThat(similar.search(a.id, a.stamp, input).voyageStatus())
                .isEqualTo(SimilarTodoSearchResponse.VoyageStatus.AVAILABLE);
        var b = actor();
        historical(b, 10, 600);
        doReturn(List.of(new float[1024])).when(voyage).embed(anyList(), anyString());
        assertThat(similar.search(b.id, b.stamp, input).voyageStatus())
                .isEqualTo(SimilarTodoSearchResponse.VoyageStatus.UNAVAILABLE);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from todo_embeddings e join todos t on"
                                    + " t.id=e.todo_id where t.user_id=?",
                                Long.class,
                                b.id))
                .isZero();
    }

    @Test
    void newDraftCannotBypassExistingTodoAndStaleOfferCannotOverwriteEditing() {
        var a = actor();
        var original =
                todoService.create(
                        a.id,
                        a.stamp,
                        new TodoCreateRequest(
                                "기획안",
                                null,
                                day(),
                                LocalTime.of(9, 0),
                                day(),
                                LocalTime.of(10, 0),
                                false,
                                10));
        UUID id = drafts.issue(a.id, a.stamp, new AiDraftRequest(original.id(), null)).draftId();
        var p = predictions.predict(a.id, a.stamp, request(id));
        todoService.update(
                a.id,
                a.stamp,
                original.id(),
                new TodoUpdateRequest(
                        "다른 편집",
                        null,
                        day(),
                        LocalTime.of(9, 0),
                        day(),
                        LocalTime.of(10, 0),
                        false,
                        10));
        var input =
                new TodoUpdateRequest(
                        "기획안",
                        null,
                        day(),
                        LocalTime.of(9, 0),
                        day(),
                        LocalTime.of(10, 0),
                        false,
                        10,
                        new EstimateChoiceRequest(
                                id, SelectedEstimateType.AI, p.predictionToken()));
        code(
                () -> todoService.update(a.id, a.stamp, original.id(), input),
                ErrorCode.AI_OFFER_INVALID);
        var fresh = draft(a);
        var bypass =
                new TodoUpdateRequest(
                        "기획안",
                        null,
                        day(),
                        LocalTime.of(9, 0),
                        day(),
                        LocalTime.of(10, 0),
                        false,
                        10,
                        new EstimateChoiceRequest(fresh, SelectedEstimateType.USER, null));
        code(
                () -> todoService.update(a.id, a.stamp, original.id(), bypass),
                ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    void savedManualChildrenCanUseTheirRemainingSinglePrediction() {
        var a = actor();
        var d = decompositions.decompose(a.id, a.stamp, decomposition(draft(a)));
        var items = tasks(d);
        var saved =
                aiTodos.confirm(
                        a.id,
                        a.stamp,
                        confirmInput(d, UUID.randomUUID(), null, SelectedEstimateType.USER, items));
        var first = saved.items().getFirst();
        var canonical = drafts.issue(a.id, a.stamp, new AiDraftRequest(first.todo().id(), null));
        assertThat(
                        predictions
                                .predict(
                                        a.id,
                                        a.stamp,
                                        new TimePredictionRequest(
                                                canonical.draftId(),
                                                first.todo().title(),
                                                "기획안",
                                                null,
                                                10))
                                .prediction()
                                .usage()
                                .state())
                .isEqualTo(AiUsageResponse.State.SUCCEEDED);
    }

    @Test
    void apiCreatesThenReplaysSingleWithCorrectStatus() throws Exception {
        var a = actor();
        UUID id = draft(a);
        var p = predictions.predict(a.id, a.stamp, request(id));
        var body =
                json.write(
                        createInput(
                                id,
                                p.predictionToken(),
                                SelectedEstimateType.AI,
                                UUID.randomUUID()));
        var first =
                mvc.perform(
                                post("/api/v1/todos")
                                        .header("Authorization", "Bearer " + a.access)
                                        .contentType("application/json")
                                        .content(body))
                        .andReturn()
                        .getResponse();
        var second =
                mvc.perform(
                                post("/api/v1/todos")
                                        .header("Authorization", "Bearer " + a.access)
                                        .contentType("application/json")
                                        .content(body))
                        .andReturn()
                        .getResponse();
        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
    }
}
