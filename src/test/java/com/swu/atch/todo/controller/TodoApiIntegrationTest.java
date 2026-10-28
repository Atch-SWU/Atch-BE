package com.swu.atch.todo.controller;

import com.swu.atch.ai.domain.*;
import com.swu.atch.ai.repository.*;
import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.jwt.JwtTokenService;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.recurrence.domain.*;
import com.swu.atch.recurrence.repository.*;
import com.swu.atch.report.domain.TodoDailySnapshot;
import com.swu.atch.report.repository.TodoDailySnapshotRepository;
import com.swu.atch.session.domain.Session;
import com.swu.atch.session.domain.SessionEndReason;
import com.swu.atch.session.repository.SessionRepository;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import com.swu.atch.todo.domain.*;
import com.swu.atch.todo.dto.request.TodoUpdateRequest;
import com.swu.atch.todo.dto.response.TodoResponse;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.todo.service.TodoSchedulePolicy;
import com.swu.atch.todo.service.TodoService;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import com.swu.atch.user.service.UserAccountService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

// 일반 사례는 롤백한다. 별도 트랜잭션·실패 주입 사례의 고유 계정은 테스트 컨테이너 폐기로 정리한다.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class TodoApiIntegrationTest extends IsolatedAuthIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 29);
    private static final String PASSWORD = "Todo123!";
    @TestBean(name = "todoSchedulePolicy", methodName = "fixedSchedule", enforceOverride = true)
    private TodoSchedulePolicy fixedSchedule;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    @Autowired AuthAccountRepository accounts;
    @Autowired TodoRepository todos;
    @Autowired SessionRepository sessions;
    @Autowired TodoDailySnapshotRepository snapshots;
    @Autowired TimePredictionExperimentRepository experiments;
    @Autowired TodoEmbeddingRepository embeddings;
    @Autowired TimeEstimationProfileRepository profiles;
    @Autowired RepeatPatternRepository patterns;
    @Autowired RecurrenceRuleRepository rules;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountCredentials credentials;
    @Autowired JwtTokenService jwt;
    @Autowired TodoService service;
    @Autowired UserAccountService userAccounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;

    static TodoSchedulePolicy fixedSchedule() {
        return new TodoSchedulePolicy(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsReadsEditsCompletesReopensAndDeletesWithoutChangingApiShape() throws Exception {
        var actor = actor();
        var response = send(actor, post("/api/v1/todos"), input("처음 작업", DAY));
        assertThat(response.getStatus()).isEqualTo(201);
        var created = json.readTree(response.getContentAsString());
        long id = created.path("id").asLong();
        assertThat(response.getHeader("Location")).isEqualTo("/api/v1/todos/" + id);
        assertThat(created.path("startAt").asText()).isEqualTo("2026-10-29T00:00:00Z");
        assertThat(created.path("status").asText()).isEqualTo("PENDING");
        assertThat(created.path("createdAt").isNull()).isFalse();
        var edited = body(actor, put(path(id)), input("바뀐 작업", DAY), 200);
        assertThat(edited.path("title").asText()).isEqualTo("바뀐 작업");
        assertThat(body(actor, get(path(id)), null, 200).path("title").asText()).isEqualTo("바뀐 작업");
        var complete = body(actor, post(path(id) + "/complete"), null, 200);
        assertThat(complete.path("completedAt").asText()).isEqualTo(NOW.toString());
        assertThat(complete.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(body(actor, post(path(id) + "/complete"), null, 200).path("completedAt")).isEqualTo(complete.path("completedAt"));
        assertThat(body(actor, put(path(id)), input("거부 작업", DAY), 409).path("code").asText()).isEqualTo("TODO_REOPEN_REQUIRED");
        var reopened = body(actor, post(path(id) + "/reopen"), null, 200);
        assertThat(reopened.path("status").asText()).isEqualTo("PENDING");
        assertThat(reopened.path("completedAt").isNull()).isTrue();
        body(actor, post(path(id) + "/complete"), null, 200);
        body(actor, delete(path(id)), null, 204);
        body(actor, delete(path(id)), null, 204);
        body(actor, get(path(id)), null, 404);
        body(actor, post(path(id) + "/reopen"), null, 404);
        assertThat(body(actor, get("/api/v1/todos"), null, 200).path("totalElements").asLong()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"detail", "update", "delete", "complete", "reopen"})
    void otherOwnerAndMissingIdReturnSame404AndPreserveData(String operation) throws Exception {
        var owner = actor(); var other = actor();
        long id = create(owner, "소유권 작업", DAY);
        var denied = body(other, operation(operation, id), operation.equals("update") ? input("침범", DAY) : null, 404);
        var absent = body(other, operation(operation, Long.MAX_VALUE), operation.equals("update") ? input("침범", DAY) : null, 404);
        assertThat(denied.path("code")).isEqualTo(absent.path("code"));
        assertThat(body(owner, get(path(id)), null, 200).path("title").asText()).isEqualTo("소유권 작업");
        assertThat(body(other, get("/api/v1/todos"), null, 200).path("totalElements").asLong()).isZero();
        assertThat(body(other, get("/api/v1/todos/calendar").param("from", DAY.toString()).param("to", DAY.toString()), null, 200).path("dates").isEmpty()).isTrue();
    }

    @Test
    void filtersBeforePagingAndSumsOnlyClosedSessionsForCurrentPage() throws Exception {
        var owner = actor(); var other = actor();
        var first = seed(owner, "첫째", DAY);
        var second = seed(owner, "둘째", DAY);
        var completed = seed(owner, "완료", DAY); completed.complete(NOW);
        var nextDay = seed(owner, "다음 날짜", DAY.plusDays(1));
        var deleted = seed(owner, "삭제", DAY); deleted.softDelete(NOW);
        seed(other, "다른 사용자", DAY);
        closed(first, 120); closed(first, 30); closed(second, 75);
        sessions.save(Session.start(first, NOW.minusSeconds(10)));
        todos.flush(); em.clear();
        var query = get("/api/v1/todos").param("from", DAY.toString()).param("to", DAY.plusDays(1).toString())
                .param("status", "PENDING,PAUSED").param("size", "2");
        var page = body(owner, query, null, 200);
        assertThat(page.path("totalElements").asLong()).isEqualTo(3);
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        assertThat(page.path("hasNext").asBoolean()).isTrue();
        assertThat(ids(page.path("items"))).containsExactly(first.getId(), second.getId());
        assertThat(page.path("items").get(0).path("actualDurationSeconds").asLong()).isEqualTo(150);
        assertThat(page.path("items").get(1).path("actualDurationSeconds").asLong()).isEqualTo(75);
        var last = body(owner, get("/api/v1/todos").param("from", DAY.toString()).param("to", DAY.plusDays(1).toString())
                .param("status", "PENDING,PAUSED").param("size", "2").param("page", "1"), null, 200);
        assertThat(ids(last.path("items"))).containsExactly(nextDay.getId());
        assertThat(last.path("hasNext").asBoolean()).isFalse();
        assertThat(last.path("items").get(0).path("actualDurationSeconds").asLong()).isZero();
        var empty = body(owner, get("/api/v1/todos").param("date", DAY.minusYears(1).toString()), null, 200);
        assertThat(empty.path("items").isEmpty()).isTrue();
        assertThat(empty.path("totalPages").asInt()).isZero();
        var done = body(owner, get("/api/v1/todos").param("date", DAY.toString()).param("status", "COMPLETED"), null, 200);
        assertThat(ids(done.path("items"))).containsExactly(completed.getId());
    }

    @Test
    void calendarUsesCurrentAssignmentAndIncludesCompletedButExcludesDeleted() throws Exception {
        var owner = actor();
        var rolled = seed(owner, "이월 픽스처", DAY.minusDays(2));
        var completed = seed(owner, "완료", DAY.plusDays(1)); completed.complete(NOW);
        var deleted = seed(owner, "삭제", DAY.plusDays(2)); deleted.softDelete(NOW);
        todos.flush();
        jdbc.update("update todos set current_schedule_date=? where id=?", DAY, rolled.getId());
        em.clear();
        var result = body(owner, get("/api/v1/todos/calendar").param("from", DAY.toString()).param("to", DAY.plusDays(2).toString()), null, 200);
        assertThat(result.path("dates").toString()).isEqualTo("[\"2026-10-29\",\"2026-10-30\"]");
        var detail = body(owner, get(path(rolled.getId())), null, 200);
        assertThat(detail.path("rolledOver").asBoolean()).isTrue();
        assertThat(detail.path("overdue").asBoolean()).isTrue();
    }

    @Test
    void allDayMemoEditPreservesRolledDateAndExplicitStartDateChangeReassigns() throws Exception {
        var owner = actor();
        var originalDate = DAY.minusDays(1);
        var todo = todos.saveAndFlush(Todo.builder().user(users.getReferenceById(owner.id())).title("하루종일")
                .startAt(originalDate.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant())
                .dueAt(DAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()).currentScheduleDate(originalDate).allDay(true)
                .userEstimatedDurationMinutes(30).userEstimateSource(UserEstimateSource.USER_INPUT)
                .selectedEstimateType(SelectedEstimateType.USER).build());
        jdbc.update("update todos set current_schedule_date=? where id=?", DAY, todo.getId()); em.clear();
        var edit = input("하루종일", originalDate); edit.put("allDay", true); edit.put("startTime", null); edit.put("dueTime", null); edit.put("memo", "메모 수정");
        var changed = body(owner, put(path(todo.getId())), edit, 200);
        assertThat(changed.path("currentScheduleDate").asText()).isEqualTo(DAY.toString());
        assertThat(changed.path("dueAt").asText()).isEqualTo("2026-10-28T15:00:00Z");
        edit.put("startDate", DAY.plusDays(1).toString()); edit.put("dueDate", DAY.plusDays(1).toString()); edit.put("memo", null);
        changed = body(owner, put(path(todo.getId())), edit, 200);
        assertThat(changed.path("currentScheduleDate").asText()).isEqualTo(DAY.plusDays(1).toString());
        assertThat(changed.path("memo").isNull()).isTrue();
        assertThat(changed.path("rolledOver").asBoolean()).isFalse();
    }

    @Test
    void unchangedInputsPreserveAiAndTemplateSourceThenChangedInputsInvalidateSelection() throws Exception {
        var owner = actor();
        var todo = todos.saveAndFlush(Todo.builder().user(users.getReferenceById(owner.id())).title("AI 작업")
                .startAt(Instant.parse("2026-10-29T00:00:00Z")).dueAt(Instant.parse("2026-10-29T01:00:00Z"))
                .currentScheduleDate(DAY).allDay(false).userEstimatedDurationMinutes(30)
                .userEstimateSource(UserEstimateSource.RECURRENCE_TEMPLATE).aiEstimatedDurationMinutes(40)
                .selectedEstimateType(SelectedEstimateType.AI).build());
        var edit = input("  AI 작업  ", DAY);
        var same = body(owner, put(path(todo.getId())), edit, 200);
        assertThat(same.path("aiEstimatedDurationMinutes").asInt()).isEqualTo(40);
        assertThat(same.path("selectedEstimateType").asText()).isEqualTo("AI");
        assertThat(same.path("userEstimateSource").asText()).isEqualTo("RECURRENCE_TEMPLATE");
        edit.put("memo", "예측 맥락 변경");
        var changed = body(owner, put(path(todo.getId())), edit, 200);
        assertThat(changed.path("aiEstimatedDurationMinutes").isNull()).isTrue();
        assertThat(changed.path("selectedEstimateType").asText()).isEqualTo("USER");
        assertThat(changed.path("estimatedDurationMinutes").asInt()).isEqualTo(30);
        assertThat(changed.path("userEstimateSource").asText()).isEqualTo("RECURRENCE_TEMPLATE");
        edit.put("userEstimatedDurationMinutes", 45);
        assertThat(body(owner, put(path(todo.getId())), edit, 200).path("userEstimateSource").asText()).isEqualTo("USER_INPUT");
    }

    @Test
    void mutationsPreserveRecurringIdentitySnapshotsExperimentsProfilesAndEmbeddings() throws Exception {
        var owner = actor(); var user = users.getReferenceById(owner.id());
        var pattern = patterns.saveAndFlush(RepeatPattern.builder().user(user).taskName("반복 원본")
                .frequency(RecurrenceFrequency.DAILY).scheduledTime(LocalTime.of(9, 0)).occurrenceCount(2).lastOccurrenceDate(DAY).build());
        var rule = rules.saveAndFlush(RecurrenceRule.builder().user(user).sourcePattern(pattern).taskName("반복 원본")
                .scheduledTime(LocalTime.of(9, 0)).defaultDeadlineOffsetMinutes(60).defaultUserEstimatedDurationMinutes(30).build());
        Instant occurrence = Instant.parse("2026-10-29T00:00:00Z");
        var todo = todos.saveAndFlush(Todo.builder().user(user).title("반복 작업").sourceTaskName("분해 원본")
                .recurrenceRuleId(rule.getId()).recurrenceOccurrenceAt(occurrence).recurrencePeriodStartDate(DAY)
                .startAt(occurrence).dueAt(occurrence.plusSeconds(3600)).currentScheduleDate(DAY).allDay(false)
                .userEstimatedDurationMinutes(30).userEstimateSource(UserEstimateSource.RECURRENCE_TEMPLATE)
                .selectedEstimateType(SelectedEstimateType.USER).build());
        snapshots.saveAndFlush(TodoDailySnapshot.capture(todo, DAY.minusDays(1), false, false, NOW.minusSeconds(3600)));
        experiments.saveAndFlush(TimePredictionExperiment.create(UUID.randomUUID(), todo, PredictionStrategy.HYBRID, 40));
        embeddings.saveAndFlush(TodoEmbedding.create(todo, new float[1024], "fixture-model", "a".repeat(64)));
        profiles.saveAndFlush(TimeEstimationProfile.create(user, 1.2, 2, 0.1, 0.5, 0.7));
        closed(todo, 60);
        Map<String, Object> before = historicalRows(todo.getId(), owner.id());
        var edit = input("편집된 반복 작업", DAY.plusDays(1)); edit.put("userEstimatedDurationMinutes", 45);
        var changed = body(owner, put(path(todo.getId())), edit, 200);
        assertThat(changed.path("recurrenceRuleId").asLong()).isEqualTo(rule.getId());
        assertThat(changed.path("recurrenceOccurrenceAt").asText()).isEqualTo(occurrence.toString());
        assertThat(changed.path("recurrencePeriodStartDate").asText()).isEqualTo(DAY.toString());
        assertThat(changed.path("sourceTaskName").asText()).isEqualTo("분해 원본");
        body(owner, post(path(todo.getId()) + "/complete"), null, 200);
        assertThat(body(owner, post(path(todo.getId()) + "/reopen"), null, 200).path("actualDurationSeconds").asLong()).isEqualTo(60);
        body(owner, delete(path(todo.getId())), null, 204);
        assertThat(historicalRows(todo.getId(), owner.id())).isEqualTo(before);
        assertThat(jdbc.queryForObject("select task_name from recurrence_rules where id=?", String.class, rule.getId())).isEqualTo("반복 원본");
    }

    @ParameterizedTest
    @CsvSource({"IN_PROGRESS,false", "PENDING,true", "PAUSED,true"})
    void editAndDeleteRequirePauseBasedOnStatusOrOpenSession(String status, boolean open) throws Exception {
        var owner = actor(); var todo = seed(owner, "실행 보호", DAY);
        if (open) sessions.saveAndFlush(Session.start(todo, NOW.minusSeconds(10)));
        todos.flush(); jdbc.update("update todos set status=? where id=?", status, todo.getId()); em.clear();
        assertThat(body(owner, put(path(todo.getId())), input("바뀌면 안 됨", DAY), 409).path("code").asText()).isEqualTo("TODO_PAUSE_REQUIRED");
        assertThat(body(owner, delete(path(todo.getId())), null, 409).path("code").asText()).isEqualTo("TODO_PAUSE_REQUIRED");
        assertThat(jdbc.queryForObject("select title from todos where id=?", String.class, todo.getId())).isEqualTo("실행 보호");
        assertThat(jdbc.queryForObject("select deleted_at from todos where id=?", Object.class, todo.getId())).isNull();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 90})
    void completingOpenSessionStoresActualSecondsOnceIncludingZero(long seconds) throws Exception {
        var owner = actor(); var todo = seed(owner, "집중 완료", DAY); closed(todo, 20);
        var open = sessions.saveAndFlush(Session.start(todo, NOW.minusSeconds(seconds)));
        jdbc.update("update todos set status='IN_PROGRESS' where id=?", todo.getId()); em.clear();
        var result = body(owner, post(path(todo.getId()) + "/complete"), null, 200);
        assertThat(result.path("actualDurationSeconds").asLong()).isEqualTo(20 + seconds);
        assertThat(result.path("overdue").asBoolean()).isFalse();
        var row = jdbc.queryForMap("select duration_seconds,end_reason from sessions where id=?", open.getId());
        assertThat(row.get("duration_seconds")).isEqualTo((int) seconds);
        assertThat(row.get("end_reason")).isEqualTo("COMPLETED");
        assertThat(body(owner, post(path(todo.getId()) + "/complete"), null, 200).path("actualDurationSeconds").asLong()).isEqualTo(20 + seconds);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "PAUSED", "IN_PROGRESS"})
    void reopeningIncompleteTodoDoesNotResetItsCurrentState(String status) throws Exception {
        var owner = actor(); var todo = seed(owner, "미완료 유지", DAY);
        if (status.equals("IN_PROGRESS")) sessions.saveAndFlush(Session.start(todo, NOW.minusSeconds(30)));
        jdbc.update("update todos set status=? where id=?", status, todo.getId()); em.clear();
        assertThat(body(owner, post(path(todo.getId()) + "/reopen"), null, 200).path("status").asText()).isEqualTo(status);
        if (status.equals("IN_PROGRESS")) assertThat(jdbc.queryForObject("select ended_at from sessions where todo_id=?", Object.class, todo.getId())).isNull();
    }

    @Test
    void rejectsPastCreationButAllowsTodaysPastTimeAndDetectsDueBoundary() throws Exception {
        var owner = actor();
        body(owner, post("/api/v1/todos"), input("과거 날짜", DAY.minusDays(1)), 400);
        long id = create(owner, "오늘 지난 시각", DAY);
        jdbc.update("update todos set due_at=? where id=?", java.sql.Timestamp.from(NOW), id); em.clear();
        assertThat(body(owner, get(path(id)), null, 200).path("overdue").asBoolean()).isTrue();
        jdbc.update("update todos set due_at=? where id=?", java.sql.Timestamp.from(NOW.plusNanos(1000)), id); em.clear();
        assertThat(body(owner, get(path(id)), null, 200).path("overdue").asBoolean()).isFalse();
    }

    @Test
    void invalidScheduleAndQueryCombinationsReturn400WithoutCreatingTodos() throws Exception {
        var owner = actor(); var invalid = input("잘못된 일정", DAY); invalid.put("dueTime", "08:00:00");
        body(owner, post("/api/v1/todos"), invalid, 400);
        for (var request : List.of(get("/api/v1/todos").param("date", DAY.toString()).param("from", DAY.toString()),
                get("/api/v1/todos").param("from", DAY.toString()),
                get("/api/v1/todos/calendar").param("from", DAY.toString()).param("to", DAY.minusDays(1).toString()))) {
            body(owner, request, null, 400);
        }
        assertThat(body(owner, get("/api/v1/todos"), null, 200).path("totalElements").asLong()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void inconsistentOpenSessionAbortsCompletionWithoutRepair(boolean alreadyCompleted) throws Exception {
        var owner = actor(); var todo = seed(owner, "불일치 보호", DAY);
        if (alreadyCompleted) todo.complete(NOW.minusSeconds(1));
        var open = sessions.saveAndFlush(Session.start(todo, NOW.plusSeconds(alreadyCompleted ? -10 : 1)));
        todos.flush(); em.clear();
        body(owner, post(path(todo.getId()) + "/complete"), null, 409);
        assertThat(jdbc.queryForObject("select ended_at from sessions where id=?", Object.class, open.getId())).isNull();
        assertThat(jdbc.queryForObject("select status from todos where id=?", String.class, todo.getId())).isEqualTo(alreadyCompleted ? "COMPLETED" : "PENDING");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCompletionsCloseOneSessionAndKeepOneCompletionTimestamp() throws Exception {
        var owner = actor(); long id = create(owner, "동시 완료", DAY);
        sessions.saveAndFlush(Session.start(todos.findById(id).orElseThrow(), NOW.minusSeconds(90)));
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        Callable<TodoResponse> complete = () -> { start.await(); return service.complete(owner.id(), owner.stamp(), id); };
        try {
            var first = pool.submit(complete); var second = pool.submit(complete); start.countDown();
            var a = first.get(10, TimeUnit.SECONDS); var b = second.get(10, TimeUnit.SECONDS);
            assertThat(a.completedAt()).isEqualTo(b.completedAt()).isEqualTo(NOW);
            assertThat(a.actualDurationSeconds()).isEqualTo(90L);
            assertThat(b.actualDurationSeconds()).isEqualTo(90L);
            assertThat(jdbc.queryForObject("select count(*) from sessions where todo_id=? and ended_at is not null", Long.class, id)).isEqualTo(1L);
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void passwordChangeHoldingUserLockRejectsLateTodoWriteWithOldStamp() throws Exception {
        var owner = actor(); long id = create(owner, "경합 전 작업", DAY);
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var attempted = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var password = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                users.findByIdForUpdate(owner.id()).orElseThrow();
                userAccounts.changePassword(owner.id(), owner.stamp(), PASSWORD, "Changed123!");
                locked.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return true;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var write = pool.submit(() -> {
                attempted.countDown();
                return assertThrows(BusinessException.class, () -> service.update(owner.id(), owner.stamp(), id,
                        new TodoUpdateRequest("거부할 변경", null, DAY, LocalTime.of(9, 0), DAY, LocalTime.of(10, 0), false, 30))).getErrorCode();
            });
            assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue(); release.countDown();
            assertThat(password.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(write.get(10, TimeUnit.SECONDS)).isEqualTo(ErrorCode.UNAUTHORIZED);
            assertThat(jdbc.queryForObject("select title from todos where id=?", String.class, id)).isEqualTo("경합 전 작업");
            body(owner, get(path(id)), null, 401);
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseFailureRollsBackBothClosedSessionAndTodoCompletion() throws Exception {
        var owner = actor(); long id = create(owner, "롤백 보호", DAY);
        var open = sessions.saveAndFlush(Session.start(todos.findById(id).orElseThrow(), NOW.minusSeconds(90)));
        String name = "todo_test_" + UUID.randomUUID().toString().replace("-", "");
        // 마이그레이션이 아닌 격리 DB 전용 실패 픽스처. 특정 Todo의 완료 UPDATE만 실패시킨다.
        jdbc.execute("create function " + name + "() returns trigger language plpgsql as $$ begin "
                + "if new.id=" + id + " and new.status='COMPLETED' then raise exception 'synthetic rollback' using errcode='23514'; end if; return new; end $$");
        try {
            jdbc.execute("create trigger " + name + " before update on todos for each row execute function " + name + "()");
            body(owner, post(path(id) + "/complete"), null, 500);
            assertThat(jdbc.queryForObject("select status from todos where id=?", String.class, id)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("select ended_at from sessions where id=?", Object.class, open.getId())).isNull();
            assertThat(jdbc.queryForObject("select duration_seconds from sessions where id=?", Object.class, open.getId())).isNull();
        } finally {
            jdbc.execute("drop trigger if exists " + name + " on todos");
            jdbc.execute("drop function " + name + "()");
        }
    }

    private Actor actor() {
        var user = users.saveAndFlush(new User("투두검증"));
        var account = accounts.saveAndFlush(AuthAccount.local(user, UUID.randomUUID() + "@atch.invalid", encoder.encode(PASSWORD)));
        String stamp = credentials.stamp(account);
        return new Actor(user.getId(), jwt.issueAccessToken(user.getId(), stamp), stamp);
    }

    private Todo seed(Actor owner, String title, LocalDate date) {
        Instant start = date.atTime(9, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
        return todos.saveAndFlush(Todo.builder().user(users.getReferenceById(owner.id())).title(title)
                .startAt(start).dueAt(start.plusSeconds(3600)).currentScheduleDate(date).allDay(false)
                .userEstimatedDurationMinutes(30).userEstimateSource(UserEstimateSource.USER_INPUT)
                .selectedEstimateType(SelectedEstimateType.USER).build());
    }

    private void closed(Todo todo, long seconds) {
        var session = Session.start(todo, NOW.minusSeconds(seconds)); session.finish(NOW, SessionEndReason.PAUSED);
        sessions.saveAndFlush(session);
    }

    private long create(Actor owner, String title, LocalDate date) throws Exception {
        return body(owner, post("/api/v1/todos"), input(title, date), 201).path("id").asLong();
    }

    private Map<String, Object> historicalRows(Long todoId, Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String table : List.of("todo_daily_snapshots", "time_prediction_experiments", "todo_embeddings", "sessions")) {
            result.put(table, jdbc.queryForObject("select jsonb_agg(to_jsonb(t))::text from " + table + " t where todo_id=?", String.class, todoId));
        }
        result.put("profile", jdbc.queryForObject("select to_jsonb(t)::text from time_estimation_profiles t where user_id=?", String.class, userId));
        return result;
    }

    private Map<String, Object> input(String title, LocalDate date) {
        var body = new LinkedHashMap<String, Object>();
        body.put("title", title); body.put("memo", null); body.put("startDate", date.toString()); body.put("startTime", "09:00:00");
        body.put("dueDate", date.toString()); body.put("dueTime", "10:00:00"); body.put("allDay", false); body.put("userEstimatedDurationMinutes", 30);
        return body;
    }

    private org.springframework.mock.web.MockHttpServletResponse send(Actor actor, MockHttpServletRequestBuilder request, Map<String, Object> input) throws Exception {
        request.header("Authorization", "Bearer " + actor.token());
        if (input != null) request.contentType("application/json").content(json.writeValueAsString(input));
        return mvc.perform(request).andReturn().getResponse();
    }

    private JsonNode body(Actor actor, MockHttpServletRequestBuilder request, Map<String, Object> input, int expected) throws Exception {
        var response = send(actor, request, input);
        assertThat(response.getStatus()).isEqualTo(expected);
        return response.getContentAsString().isBlank() ? json.nullNode() : json.readTree(response.getContentAsString());
    }

    private MockHttpServletRequestBuilder operation(String operation, long id) {
        return switch (operation) {
            case "detail" -> get(path(id)); case "update" -> put(path(id)); case "delete" -> delete(path(id));
            default -> post(path(id) + "/" + operation);
        };
    }

    private List<Long> ids(JsonNode array) {
        var result = new ArrayList<Long>(); for (var item : array) result.add(item.path("id").asLong()); return result;
    }

    private String path(long id) { return "/api/v1/todos/" + id; }
    private record Actor(Long id, String token, String stamp) { }
}
