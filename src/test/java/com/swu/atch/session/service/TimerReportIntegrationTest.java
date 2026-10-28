package com.swu.atch.session.service;

import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.jwt.JwtTokenService;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.report.service.*;
import com.swu.atch.session.dto.request.TimerStartRequest;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import com.swu.atch.todo.domain.*;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.todo.service.TodoSchedulePolicy;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import com.swu.atch.user.service.UserAccountService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

// HTTP 요청별 실제 커밋을 사용한다. 고유 계정의 데이터는 테스트 컨테이너 종료로 폐기한다.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@org.springframework.test.context.TestPropertySource(properties="springdoc.api-docs.enabled=true")
class TimerReportIntegrationTest extends IsolatedAuthIntegrationTest {
    static final LocalDate DAY=LocalDate.of(2026,10,29);
    static final Instant NOON=Instant.parse("2026-10-29T03:00:00Z");
    static final AtomicReference<Instant> NOW=new AtomicReference<>(NOON);
    static final java.util.concurrent.atomic.AtomicInteger ADVANCE_AFTER_READS=new java.util.concurrent.atomic.AtomicInteger(-1);
    @TestBean(name="todoSchedulePolicy",methodName="mutableSchedule",enforceOverride=true) TodoSchedulePolicy policy;
    static TodoSchedulePolicy mutableSchedule(){return new TodoSchedulePolicy(new Clock(){
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){
            Instant result=NOW.get();
            if(ADVANCE_AFTER_READS.get()>0 && ADVANCE_AFTER_READS.decrementAndGet()==0) NOW.set(result.plusSeconds(2));
            return result;
        }
    });}
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    @Autowired AuthAccountRepository accounts;
    @Autowired TodoRepository todos;
    @Autowired AccountCredentials credentials;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenService tokens;
    @Autowired TimerService timer;
    @Autowired DailySettlementService settlements;
    @Autowired UserAccountService userAccounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired com.swu.atch.todo.service.TodoService todoService;
    @BeforeEach void resetClock(){NOW.set(NOON);ADVANCE_AFTER_READS.set(-1);}

    @Test void startPauseResumeCompleteAndSummaryStoreActualSeconds() throws Exception {
        var a=actor();long todo=create(a,"집중",DAY);
        var running=body(a,post(path(todo)+"/timer/start"),"{}",200);long first=running.path("sessionId").asLong();
        NOW.set(NOON.plusSeconds(12));
        assertThat(body(a,get("/api/v1/timer/current"),null,200).path("currentSessionElapsedSeconds").asLong()).isEqualTo(12);
        var paused=body(a,post(path(todo)+"/timer/pause"),"{\"sessionId\":"+first+"}",200);
        assertThat(paused.path("state").asText()).isEqualTo("PAUSED");assertThat(paused.path("closedDurationSeconds").asLong()).isEqualTo(12);
        body(a,post(path(todo)+"/timer/pause"),"{\"sessionId\":"+first+"}",409);
        var resumed=body(a,post(path(todo)+"/timer/resume"),"{\"lastSessionId\":"+first+"}",200);
        assertThat(resumed.path("sessionId").asLong()).isNotEqualTo(first);
        body(a,post(path(todo)+"/timer/resume"),"{\"lastSessionId\":"+first+"}",409);
        NOW.set(NOON.plusSeconds(30));
        assertThat(body(a,post(path(todo)+"/complete"),null,200).path("actualDurationSeconds").asLong()).isEqualTo(30);
        var summary=body(a,get(path(todo)+"/completion-summary"),null,200);
        assertThat(summary.path("pauseCount").asLong()).isEqualTo(1);assertThat(summary.path("resumeCount").asLong()).isEqualTo(1);
        assertThat(summary.path("switchCount").asLong()).isZero();
        assertThat(body(a,get("/api/v1/timer/current"),null,200).path("state").asText()).isEqualTo("IDLE");
        var history=body(a,get(path(todo)+"/sessions").param("size","1"),null,200);
        assertThat(history.path("totalElements").asLong()).isEqualTo(2);assertThat(history.path("hasNext").asBoolean()).isTrue();
        body(a,post(path(todo)+"/reopen"),null,200);
        body(a,post(path(todo)+"/timer/start"),"{\"expectedLastSessionId\":"+resumed.path("sessionId").asLong()+"}",200);
        body(a,post(path(todo)+"/complete"),null,200);
        assertThat(body(a,get(path(todo)+"/completion-summary"),null,200).path("resumeCount").asLong()).isEqualTo(1);
    }

    @Test void zeroSecondsAndOwnershipAndStaleSessionAreEnforced() throws Exception {
        var a=actor();var other=actor();long todo=create(a,"즉시 정지",DAY);
        var started=body(a,post(path(todo)+"/timer/start"),"{}",200);long sid=started.path("sessionId").asLong();
        body(other,post(path(todo)+"/timer/pause"),"{\"sessionId\":"+sid+"}",404);
        body(a,post(path(todo)+"/timer/pause"),"{\"sessionId\":"+(sid+100)+"}",409);
        body(a,post(path(todo)+"/timer/pause"),"{\"sessionId\":"+sid+"}",200);
        assertThat(jdbc.queryForObject("select duration_seconds from sessions where id=?",Integer.class,sid)).isZero();
        body(other,get(path(todo)+"/sessions"),null,404);
    }

    @Test void runningAndPausedFocusBothRequireWaitAndRepeatedRequestsKeepReadyAt() throws Exception {
        var a=actor();long source=create(a,"원래 작업",DAY),target=create(a,"새 작업",DAY);
        long sid=body(a,post(path(source)+"/timer/start"),"{}",200).path("sessionId").asLong();
        NOW.set(NOON.plusSeconds(20));body(a,post(path(target)+"/timer/start"),"{}",409);
        String request=switchBody(source,target,sid);
        var waiting=body(a,post("/api/v1/timer/switches"),request,200);String id=waiting.path("switchId").asText();
        assertThat(waiting.path("state").asText()).isEqualTo("WAITING");
        assertThat(jdbc.queryForObject("select end_reason from sessions where id=?",String.class,sid)).isEqualTo("SWITCHED");
        NOW.set(NOON.plusSeconds(80));
        var repeated=body(a,post("/api/v1/timer/switches"),request,200);
        assertThat(repeated.path("switchId")).isEqualTo(waiting.path("switchId"));assertThat(repeated.path("readyAt")).isEqualTo(waiting.path("readyAt"));
        body(a,patch("/api/v1/settings/timer"),"{\"taskSwitchRestrictionEnabled\":false,\"taskSwitchWaitMinutes\":1}",200);
        body(a,post(path(target)+"/timer/start"),"{}",409);
        body(a,post("/api/v1/timer/switches/"+id+"/confirm"),null,409);
        NOW.set(NOON.plusSeconds(620));
        var switched=body(a,post("/api/v1/timer/switches/"+id+"/confirm"),null,200);
        assertThat(switched.path("state").asText()).isEqualTo("STARTED");
        assertThat(switched.path("timer").path("focusTodoId").asLong()).isEqualTo(target);
        body(a,post("/api/v1/timer/switches/"+id+"/confirm"),null,200);
        body(a,post("/api/v1/timer/switches"),request,200);
        assertThat(jdbc.queryForObject("select count(*) from sessions s join todos t on t.id=s.todo_id where t.user_id=?",Long.class,a.id)).isEqualTo(2);
        body(a,delete("/api/v1/timer/switches/"+id),null,409);
    }

    @Test void cancelledExpiredAndPausedSwitchesDoNotReleaseLogicalFocus() throws Exception {
        var a=actor();long source=create(a,"계속할 작업",DAY),target=create(a,"전환할 작업",DAY);
        long sid=body(a,post(path(source)+"/timer/start"),"{}",200).path("sessionId").asLong();
        body(a,post(path(source)+"/timer/pause"),"{\"sessionId\":"+sid+"}",200);
        var waiting=body(a,post("/api/v1/timer/switches"),switchBody(source,target,sid),200);
        String requestPath="/api/v1/timer/switches/"+waiting.path("switchId").asText();
        body(a,delete(requestPath),null,204);body(a,delete(requestPath),null,204);
        body(a,post(path(target)+"/timer/start"),"{}",409);
        assertThat(body(a,get("/api/v1/timer/current"),null,200).path("state").asText()).isEqualTo("PAUSED");
        var again=body(a,post("/api/v1/timer/switches"),switchBody(source,target,sid),200);
        NOW.set(Instant.parse(again.path("expiresAt").asText()));
        body(a,post("/api/v1/timer/switches/"+again.path("switchId").asText()+"/confirm"),null,409);
        body(a,get("/api/v1/timer/current"),null,200);
        assertThat(jdbc.queryForObject("select state from timer_switch_requests where request_id=?::uuid",String.class,again.path("switchId").asText())).isEqualTo("EXPIRED");
        body(a,post(path(target)+"/timer/start"),"{}",409);
    }

    @Test void disabledRestrictionStartsSwitchImmediatelyAndForeignRequestIs404() throws Exception {
        var a=actor();var other=actor();long source=create(a,"A",DAY),target=create(a,"B",DAY);
        body(a,patch("/api/v1/settings/timer"),"{\"taskSwitchRestrictionEnabled\":false}",200);
        long sid=body(a,post(path(source)+"/timer/start"),"{}",200).path("sessionId").asLong();
        var result=body(a,post("/api/v1/timer/switches"),switchBody(source,target,sid),200);
        assertThat(result.path("state").asText()).isEqualTo("STARTED");
        body(other,post("/api/v1/timer/switches/"+result.path("switchId").asText()+"/confirm"),null,404);
        assertThat(body(a,get("/api/v1/settings/timer"),null,200).path("taskSwitchWaitMinutes").asInt()).isEqualTo(10);
    }

    @Test void concurrentStartsAcrossTwoTodosAllowExactlyOneOpenSession() throws Exception {
        var a=actor();long first=create(a,"첫째",DAY),second=create(a,"둘째",DAY);
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(long id:List.of(first,second)) results.add(pool.submit(()->{
                gate.await();try{timer.start(a.id,a.stamp,id,new TimerStartRequest(null));return true;}catch(BusinessException ex){assertThat(ex.getErrorCode().getStatus().value()).isEqualTo(409);return false;}
            }));
            gate.countDown();int success=0;for(var result:results) if(result.get(10,TimeUnit.SECONDS))success++;
            assertThat(success).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from sessions s join todos t on t.id=s.todo_id where t.user_id=? and s.ended_at is null",Long.class,a.id)).isEqualTo(1);
        }finally{gate.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }

    @Test void midnightKeepsOpenSessionAndSeparatesSnapshotFromCurrentCompletion() throws Exception {
        NOW.set(Instant.parse("2026-10-29T14:50:00Z"));var a=actor();long todo=create(a,"자정 집중",DAY);
        long sid=body(a,post(path(todo)+"/timer/start"),"{}",200).path("sessionId").asLong();
        NOW.set(Instant.parse("2026-10-29T15:20:00Z"));
        var current=body(a,get("/api/v1/timer/current"),null,200);
        assertThat(current.path("sessionId").asLong()).isEqualTo(sid);assertThat(current.path("currentSessionElapsedSeconds").asLong()).isEqualTo(1800);
        assertThat(jdbc.queryForObject("select status_at_day_end from todo_daily_snapshots where todo_id=?",String.class,todo)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select current_schedule_date from todos where id=?",LocalDate.class,todo)).isEqualTo(DAY.plusDays(1));
        body(a,post(path(todo)+"/complete"),null,200);
        var yesterday=daily(a,DAY);var today=daily(a,DAY.plusDays(1));
        assertThat(yesterday.path("state").asText()).isEqualTo("FINALIZED");assertThat(yesterday.path("closedFocusDurationSeconds").asLong()).isEqualTo(600);
        assertThat(yesterday.path("completedAssignedCount").asLong()).isZero();assertThat(today.path("closedFocusDurationSeconds").asLong()).isEqualTo(1200);
        assertThat(today.path("completedTodoCount").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select duration_seconds from sessions where id=?",Integer.class,sid)).isEqualTo(1800);
        body(a,post(path(todo)+"/reopen"),null,200);
        assertThat(daily(a,DAY).path("completedAssignedCount").asLong()).isZero();
        assertThat(daily(a,DAY.plusDays(1)).path("completedTodoCount").asLong()).isZero();
    }

    @Test void oldCompletedReopenMovesToTodayWithoutChangingFinalizedSnapshot() throws Exception {
        var a=actor();long todo=create(a,"어제 완료",DAY);body(a,post(path(todo)+"/complete"),null,200);
        NOW.set(NOON.plusSeconds(86400));daily(a,DAY);
        var before=jdbc.queryForMap("select * from todo_daily_snapshots where todo_id=?",todo);
        var reopened=body(a,post(path(todo)+"/reopen"),null,200);
        assertThat(reopened.path("currentScheduleDate").asText()).isEqualTo(DAY.plusDays(1).toString());
        assertThat(jdbc.queryForMap("select * from todo_daily_snapshots where todo_id=?",todo)).isEqualTo(before);
        assertThat(daily(a,DAY).path("completedAssignedCount").asLong()).isEqualTo(1);
        assertThat(daily(a,DAY).path("completedTodoCount").asLong()).isZero();
    }

    @Test void missedDaysAndEmptyDaysAreFinalizedOnceAndReloadCursor() throws Exception {
        var a=actor();long todo=create(a,"반복 이월",DAY);
        NOW.set(NOON.plusSeconds(3*86400));assertThat(settlements.prepare(a.id,a.stamp)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from todo_daily_snapshots where todo_id=?",Long.class,todo)).isEqualTo(3);
        var before=jdbc.queryForList("select * from todo_daily_snapshots where todo_id=? order by id",todo);
        // 새 요청은 DB의 진행 위치와 전환 상태를 다시 읽는다. 메모리 카운터에 의존하지 않는다.
        var restored=context.getAutowireCapableBeanFactory().createBean(DailySettlementService.class);
        assertThat(restored).isNotSameAs(settlements);
        assertThat(restored.prepare(a.id,a.stamp)).isTrue();assertThat(jdbc.queryForList("select * from todo_daily_snapshots where todo_id=? order by id",todo)).isEqualTo(before);
        body(a,post(path(todo)+"/complete"),null,200);NOW.set(NOON.plusSeconds(5*86400));
        assertThat(daily(a,DAY.plusDays(4)).path("assignedCount").asLong()).isZero();
        assertThat(daily(a,DAY.plusDays(4)).path("state").asText()).isEqualTo("FINALIZED");
        assertThat(daily(a,DAY.plusDays(4)).path("completionRate").isNull()).isTrue();
    }

    @Test void failedSnapshotInsertRollsBackRolloverAndCursorThenRetries() throws Exception {
        var a=actor();long todo=create(a,"실패 복구",DAY);NOW.set(NOON.plusSeconds(86400));
        String name="snapshot_test_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("create function "+name+"() returns trigger language plpgsql as $$ begin if new.todo_id="+todo+" then raise exception 'synthetic snapshot failure' using errcode='23514'; end if; return new; end $$");
        try{
            jdbc.execute("create trigger "+name+" before insert on todo_daily_snapshots for each row execute function "+name+"()");
            assertThrows(RuntimeException.class,()->settlements.prepare(a.id,a.stamp));
            assertThat(jdbc.queryForObject("select current_schedule_date from todos where id=?",LocalDate.class,todo)).isEqualTo(DAY);
            assertThat(jdbc.queryForObject("select next_settlement_date from user_daily_settlement_states where user_id=?",LocalDate.class,a.id)).isEqualTo(DAY);
            assertThat(jdbc.queryForObject("select count(*) from todo_daily_snapshots where todo_id=?",Long.class,todo)).isZero();
        }finally{jdbc.execute("drop trigger if exists "+name+" on todo_daily_snapshots");jdbc.execute("drop function "+name+"()");}
        assertThat(settlements.prepare(a.id,a.stamp)).isTrue();assertThat(daily(a,DAY).path("assignedCount").asLong()).isEqualTo(1);
    }

    @Test void concurrentCatchUpAndRequestDoNotDuplicateSnapshots() throws Exception {
        var a=actor();long todo=create(a,"동시 확정",DAY);NOW.set(NOON.plusSeconds(2*86400));
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{
            var first=pool.submit(()->{gate.await();return settlements.prepare(a.id,a.stamp);});
            var second=pool.submit(()->{gate.await();return timer.current(a.id,a.stamp);});gate.countDown();
            assertThat(first.get(10,TimeUnit.SECONDS)).isTrue();second.get(10,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("select count(*) from todo_daily_snapshots where todo_id=?",Long.class,todo)).isEqualTo(2);
        }finally{gate.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }

    @Test void initialAdoptionMovesLegacyIncompleteButDoesNotInventPastDays() throws Exception {
        var a=actor();Instant start=NOON.minusSeconds(2*86400);
        var todo=todos.saveAndFlush(Todo.builder().user(users.findById(a.id).orElseThrow()).title("기존 미완료")
                .startAt(start).dueAt(start.plusSeconds(3600)).currentScheduleDate(DAY.minusDays(2)).allDay(false)
                .userEstimatedDurationMinutes(30).userEstimateSource(UserEstimateSource.USER_INPUT).selectedEstimateType(SelectedEstimateType.USER).build());
        assertThat(daily(a,DAY.minusDays(1)).path("state").asText()).isEqualTo("UNAVAILABLE");
        assertThat(daily(a,DAY.minusDays(1)).path("assignedCount").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select current_schedule_date from todos where id=?",LocalDate.class,todo.getId())).isEqualTo(DAY);
        assertThat(jdbc.queryForObject("select count(*) from todo_daily_snapshots where todo_id=?",Long.class,todo.getId())).isZero();
    }

    @Test void longCatchUpCommitsBoundedBatchesAndReportsPendingInsteadOfInventedZero() throws Exception {
        var a=actor();create(a,"장기 중단",DAY);NOW.set(NOON.plusSeconds(70*86400));
        assertThat(settlements.prepare(a.id,a.stamp)).isFalse();
        assertThat(jdbc.queryForObject("select next_settlement_date from user_daily_settlement_states where user_id=?",LocalDate.class,a.id)).isEqualTo(DAY.plusDays(31));
        var pending=daily(a,DAY.plusDays(69));
        assertThat(pending.path("state").asText()).isEqualTo("PENDING_FINALIZATION");assertThat(pending.path("assignedCount").isNull()).isTrue();
        assertThat(settlements.prepare(a.id,a.stamp)).isTrue();assertThat(daily(a,DAY.plusDays(69)).path("state").asText()).isEqualTo("FINALIZED");
    }

    @Test void weeklyWeightedRatesPatternsAndSignedErrorsUseRealRecords() throws Exception {
        var a=actor();long first=create(a,"완료",DAY);create(a,"미완료",DAY);
        long sid=body(a,post(path(first)+"/timer/start"),"{}",200).path("sessionId").asLong();NOW.set(NOON.plusSeconds(90));
        body(a,post(path(first)+"/complete"),null,200);
        var compare=body(a,get("/api/v1/reports/time-comparisons").param("from",DAY.toString()).param("to",DAY.toString()),null,200);
        assertThat(compare.path("items").get(0).path("actualDurationSeconds").asLong()).isEqualTo(90);
        assertThat(compare.path("items").get(0).path("userErrorSeconds").asLong()).isEqualTo(-1710);
        assertThat(compare.path("items").get(0).path("aiErrorSeconds").isNull()).isTrue();
        NOW.set(NOON.plusSeconds(86400));
        var week=body(a,get("/api/v1/reports/weekly").param("date",DAY.plusDays(1).toString()),null,200);
        // 월요일~수요일은 도입 이전이라 주간 배정 합계가 아직 확정 자료 없음이다.
        assertThat(week.path("assignedCount").isNull()).isTrue();assertThat(week.path("elapsedDayCount").asInt()).isEqualTo(5);
        var pattern=body(a,get("/api/v1/reports/focus-patterns").param("from",DAY.toString()).param("to",DAY.plusDays(1).toString()),null,200);
        long sum=0;for(var bucket:pattern.path("hours"))sum+=bucket.path("closedDurationSeconds").asLong();assertThat(sum).isEqualTo(90);
        assertThat(pattern.path("weekdays").get(3).path("totalDurationSeconds").asLong()).isEqualTo(90);
        assertThat(jdbc.queryForObject("select duration_seconds from sessions where id=?",Integer.class,sid)).isEqualTo(90);
        body(a,get("/api/v1/reports/daily").param("date",DAY.plusYears(4).toString()),null,400);
        body(a,get("/api/v1/reports/focus-patterns").param("from",DAY.toString()).param("to",DAY.minusDays(1).toString()),null,400);
    }

    @Test void waitingMetadataAndCursorAreRemovedOnWithdrawalWithoutTouchingOtherUser() throws Exception {
        var a=actor();var other=actor();long source=create(a,"삭제 A",DAY),target=create(a,"삭제 B",DAY),kept=create(other,"보존",DAY);
        long sid=body(a,post(path(source)+"/timer/start"),"{}",200).path("sessionId").asLong();
        var item=body(a,post("/api/v1/timer/switches"),switchBody(source,target,sid),200);
        // 미완료 대기·새 상태 테이블이 있어도 기존 회원탈퇴 삭제 순서를 막지 않는다.
        userAccounts.withdraw(a.id,a.stamp,"Timer123!");
        assertThat(jdbc.queryForObject("select count(*) from timer_switch_requests where user_id=?",Long.class,a.id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from user_daily_settlement_states where user_id=?",Long.class,a.id)).isZero();
        assertThat(body(other,get(path(kept)),null,200).path("title").asText()).isEqualTo("보존");
        body(a,get("/api/v1/timer/current"),null,401);
    }

    @Test void newServiceInstanceReloadsWaitAndDoesNotRestartSessionOnRepeatedConfirm() throws Exception {
        var a=actor();long source=create(a,"복원 A",DAY),target=create(a,"복원 B",DAY);
        long sid=body(a,post(path(source)+"/timer/start"),"{}",200).path("sessionId").asLong();
        var wait=body(a,post("/api/v1/timer/switches"),switchBody(source,target,sid),200);
        var restored=context.getAutowireCapableBeanFactory().createBean(TimerService.class);
        assertThat(restored).isNotSameAs(timer);
        var pending=restored.current(a.id,a.stamp);
        assertThat(pending.pendingSwitch().switchId().toString()).isEqualTo(wait.path("switchId").asText());
        NOW.set(Instant.parse(wait.path("readyAt").asText()));
        var first=restored.confirmSwitch(a.id,a.stamp,pending.pendingSwitch().switchId());
        var second=timer.confirmSwitch(a.id,a.stamp,pending.pendingSwitch().switchId());
        assertThat(second.timer().sessionId()).isEqualTo(first.timer().sessionId());
    }

    @Test void concurrentPauseAndCompleteCloseOnlyOneInterval() throws Exception {
        var a=actor();long todo=create(a,"동시 정지·완료",DAY);
        long sid=body(a,post(path(todo)+"/timer/start"),"{}",200).path("sessionId").asLong();NOW.set(NOON.plusSeconds(30));
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{
            var pause=pool.submit(()->{gate.await();try{return timer.pause(a.id,a.stamp,todo,new com.swu.atch.session.dto.request.TimerPauseRequest(sid));}
                catch(BusinessException ex){assertThat(ex.getErrorCode().getStatus().value()).isEqualTo(409);return null;}});
            var complete=pool.submit(()->{gate.await();return todoService.complete(a.id,a.stamp,todo);});gate.countDown();
            pause.get(10,TimeUnit.SECONDS);assertThat(complete.get(10,TimeUnit.SECONDS).actualDurationSeconds()).isEqualTo(30);
            assertThat(jdbc.queryForObject("select count(*) from sessions where todo_id=?",Long.class,todo)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select duration_seconds from sessions where id=?",Integer.class,sid)).isEqualTo(30);
            assertThat(jdbc.queryForObject("select status from todos where id=?",String.class,todo)).isEqualTo("COMPLETED");
        }finally{gate.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }

    @Test void newSchemaRejectsDuplicateWaitInvalidTimesStatesAndMissingReferences() throws Exception {
        var a=actor();long source=create(a,"제약 A",DAY),target=create(a,"제약 B",DAY);
        long sid=body(a,post(path(source)+"/timer/start"),"{}",200).path("sessionId").asLong();
        var wait=body(a,post("/api/v1/timer/switches"),switchBody(source,target,sid),200);String id=wait.path("switchId").asText();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("insert into timer_switch_requests(request_id,user_id,source_todo_id,target_todo_id,source_session_id,state,requested_at,ready_at,expires_at) select ?::uuid,user_id,source_todo_id,target_todo_id,source_session_id,state,requested_at,ready_at,expires_at from timer_switch_requests where request_id=?::uuid",UUID.randomUUID().toString(),id));
        for(String assignment:List.of("state='INVALID'","ready_at=requested_at-interval '1 second'","expires_at=ready_at","state='STARTED'","target_todo_id=source_todo_id","source_session_id=9223372036854775807","requested_at=null")) {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("update timer_switch_requests set "+assignment+" where request_id=?::uuid",id));
        }
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("update user_daily_settlement_states set next_settlement_date=tracking_started_on-1 where user_id=?",a.id));
        assertThat(body(a,get("/api/v1/timer/current"),null,200).path("state").asText()).isEqualTo("SWITCH_WAITING");
    }

    @Test void legacyMultipleOpenIntervalsAreReportedWithoutAutomaticDataRepair() throws Exception {
        var a=actor();long first=create(a,"기존 A",DAY),second=create(a,"기존 B",DAY);
        for(long id:List.of(first,second)){
            jdbc.update("update todos set status='IN_PROGRESS' where id=?",id);
            jdbc.update("insert into sessions(todo_id,started_at) values(?,?)",id,java.sql.Timestamp.from(NOON));
        }
        body(a,get("/api/v1/timer/current"),null,409);
        assertThat(jdbc.queryForObject("select count(*) from sessions s join todos t on t.id=s.todo_id where t.user_id=? and ended_at is null",Long.class,a.id)).isEqualTo(2);
    }

    @Test void deletingWaitingTargetCancelsWaitAndRetainsPausedSource() throws Exception {
        var a=actor();long source=create(a,"남길 작업",DAY),target=create(a,"삭제할 작업",DAY);
        long sid=body(a,post(path(source)+"/timer/start"),"{}",200).path("sessionId").asLong();
        var wait=body(a,post("/api/v1/timer/switches"),switchBody(source,target,sid),200);
        body(a,delete(path(target)),null,204);
        var current=body(a,get("/api/v1/timer/current"),null,200);
        assertThat(current.path("state").asText()).isEqualTo("PAUSED");assertThat(current.path("pendingSwitch").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select state from timer_switch_requests where request_id=?::uuid",String.class,wait.path("switchId").asText())).isEqualTo("CANCELLED");
    }

    @Test void fullKnownWeekUsesWeightedCountsAndZeroFocusDays() throws Exception {
        // 월요일부터 추적해 배정 수 2+1=3, 완료 수 1인 주간 지표를 검증한다.
        LocalDate monday=DAY.with(java.time.DayOfWeek.MONDAY);NOW.set(monday.atTime(12,0).atZone(ZoneId.of("Asia/Seoul")).toInstant());
        var a=actor();long first=create(a,"월요일 완료",monday);create(a,"이월 작업",monday);
        body(a,post(path(first)+"/complete"),null,200);NOW.set(NOW.get().plusSeconds(86400));
        var week=body(a,get("/api/v1/reports/weekly"),null,200);
        assertThat(week.path("assignedCount").asLong()).isEqualTo(3);assertThat(week.path("completedAssignedCount").asLong()).isEqualTo(1);
        assertThat(week.path("completionRate").asDouble()).isEqualTo(1.0/3);assertThat(week.path("elapsedDayCount").asInt()).isEqualTo(2);
        assertThat(week.path("averageDailyFocusDurationSeconds").asDouble()).isZero();
    }

    @Test void completeAfterPauseAndReopenDoesNotCountFirstStartAsResume() throws Exception {
        var a=actor();long todo=create(a,"일시정지 후 완료",DAY);
        long sid=body(a,post(path(todo)+"/timer/start"),"{}",200).path("sessionId").asLong();
        NOW.set(NOON.plusSeconds(10));body(a,post(path(todo)+"/timer/pause"),"{\"sessionId\":"+sid+"}",200);
        body(a,post(path(todo)+"/complete"),null,200);body(a,post(path(todo)+"/reopen"),null,200);
        assertThat(body(a,get("/api/v1/timer/current"),null,200).path("state").asText()).isEqualTo("IDLE");
        long next=body(a,post(path(todo)+"/timer/start"),"{\"expectedLastSessionId\":"+sid+"}",200).path("sessionId").asLong();
        assertThat(jdbc.queryForObject("select resume_counted from sessions where id=?",Boolean.class,next)).isFalse();
        NOW.set(NOON.plusSeconds(20));body(a,post(path(todo)+"/timer/pause"),"{\"sessionId\":"+next+"}",200);
        long resumed=body(a,post(path(todo)+"/timer/resume"),"{\"lastSessionId\":"+next+"}",200).path("sessionId").asLong();
        assertThat(jdbc.queryForObject("select resume_counted from sessions where id=?",Boolean.class,resumed)).isTrue();
        assertThat(daily(a,DAY).path("resumeCount").asLong()).isEqualTo(1);
        body(a,post(path(todo)+"/complete"),null,200);
        var summary=body(a,get(path(todo)+"/completion-summary"),null,200);
        assertThat(summary.path("resumeCount").asLong()).isEqualTo(1);assertThat(summary.path("pauseCount").asLong()).isEqualTo(2);
    }

    @Test void legacyNullResumeMarkersKeepPreviousReasonFallback() throws Exception {
        var a=actor();long todo=create(a,"기존 구간",DAY);
        jdbc.update("insert into sessions(todo_id,started_at,ended_at,duration_seconds,end_reason) values(?,?,?,?,?)",todo,
                java.sql.Timestamp.from(NOON.minusSeconds(30)),java.sql.Timestamp.from(NOON.minusSeconds(20)),10,"PAUSED");
        jdbc.update("insert into sessions(todo_id,started_at,ended_at,duration_seconds,end_reason) values(?,?,?,?,?)",todo,
                java.sql.Timestamp.from(NOON.minusSeconds(10)),java.sql.Timestamp.from(NOON),10,"COMPLETED");
        assertThat(daily(a,DAY).path("resumeCount").asLong()).isEqualTo(1);
    }

    @Test void oneCommandUsesSameServerInstantEvenWhenWallClockCrossesMidnight() throws Exception {
        var a=actor();long todo=create(a,"경계 시각 완료",DAY);
        Instant beforeMidnight=Instant.parse("2026-10-29T14:59:59Z");NOW.set(beforeMidnight);
        // User 잠금·최종 확정 검사를 마친 직후 시계가 다음 날짜로 넘어간다.
        ADVANCE_AFTER_READS.set(2);
        var completed=body(a,post(path(todo)+"/complete"),null,200);
        assertThat(completed.path("completedAt").asText()).isEqualTo(beforeMidnight.toString());
        assertThat(daily(a,DAY).path("completedAssignedCount").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select completed_at_snapshot from todo_daily_snapshots where todo_id=?",java.sql.Timestamp.class,todo).toInstant()).isEqualTo(beforeMidnight);
    }

    @Test void defaultRuntimeExposesImplementedOpenApiAndRecordsIsolatedTargets() throws Exception {
        assertThat(context.containsBean("sessionController")).isTrue();assertThat(context.containsBean("reportController")).isTrue();
        assertThat(context.containsBean("taskDecompositionController")).isFalse();assertThat(context.containsBean("voyageClient")).isFalse();
        assertThat(context.containsBean("dailySettlementScheduler")).isFalse();
        var response=mvc.perform(get("/v3/api-docs")).andReturn().getResponse();assertThat(response.getStatus()).isEqualTo(200);
        var api=json.readTree(response.getContentAsString());
        for(String path:List.of("/api/v1/timer/current","/api/v1/todos/{todoId}/timer/start","/api/v1/reports/daily","/api/v1/settings/timer")){
            assertThat(api.path("paths").has(path)).isTrue();assertThat(api.path("paths").path(path).toString()).doesNotContain("현재 501","Service 미연결");
        }
        assertThat(api.path("paths").has("/api/v1/ai/task-decompositions")).isFalse();
        var directory=java.nio.file.Path.of("build/verification/session-report-20261029");java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve("openapi.json"),response.getContentAsString());
        var ds=context.getBean(javax.sql.DataSource.class);var flyway=context.getBean(org.flywaydb.core.Flyway.class);
        var redis=context.getBean(org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory.class);
        assertThat(flyway.getConfiguration().getDataSource()).isSameAs(ds);
        try(var connection=ds.getConnection()){
            var proof=Map.of("jdbc",connection.getMetaData().getURL(),"database",connection.getCatalog(),
                    "postgresVersion",jdbc.queryForObject("show server_version",String.class),
                    "vectorVersion",jdbc.queryForObject("select extversion from pg_extension where extname='vector'",String.class),
                    "flywayVersion",flyway.info().current().getVersion().toString(),"flywaySameDataSource",true,
                    "redis",redis.getHostName()+":"+redis.getPort(),"config","classpath:/application-auth-test.properties",
                    "rsa","in-memory test key","externalClientsEnabled",false);
            java.nio.file.Files.writeString(directory.resolve("runtime-evidence.json"),json.writeValueAsString(proof));
        }
    }

    @Test void overdueRolloverAndCurrentTitleRemainIndependentAfterEditAndDelete() throws Exception {
        NOW.set(Instant.parse("2026-10-28T23:00:00Z"));var a=actor();long done=create(a,"일찍 완료",DAY);
        body(a,post(path(done)+"/complete"),null,200);
        String form="{\"title\":\"기한 여유\",\"startDate\":\""+DAY+"\",\"startTime\":\"09:00\",\"dueDate\":\""+DAY.plusDays(2)+"\",\"dueTime\":\"10:00\",\"allDay\":false,\"userEstimatedDurationMinutes\":30}";
        long pending=body(a,post("/api/v1/todos"),form,201).path("id").asLong();NOW.set(NOON.plusSeconds(86400));
        var previous=body(a,get("/api/v1/reports/daily/todos").param("date",DAY.toString()),null,200);
        for(var item:previous.path("items")){assertThat(item.path("wasRolledOver").asBoolean()).isFalse();assertThat(item.path("wasOverdue").asBoolean()).isFalse();}
        var current=body(a,get(path(pending)),null,200);assertThat(current.path("rolledOver").asBoolean()).isTrue();assertThat(current.path("overdue").asBoolean()).isFalse();
        var before=jdbc.queryForMap("select * from todo_daily_snapshots where todo_id=?",pending);
        body(a,put(path(pending)),form.replace("기한 여유","바뀐 현재 제목"),200);
        var history=body(a,get("/api/v1/reports/daily/todos").param("date",DAY.toString()),null,200);
        assertThat(history.path("items").get(1).path("currentTitle").asText()).isEqualTo("바뀐 현재 제목");
        body(a,delete(path(pending)),null,204);
        assertThat(jdbc.queryForMap("select * from todo_daily_snapshots where todo_id=?",pending)).isEqualTo(before);
        assertThat(daily(a,DAY).path("assignedCount").asLong()).isEqualTo(2);
        body(a,get("/api/v1/reports/daily").param("date",DAY.plusDays(1).minusYears(3).toString()),null,200);
        body(a,get("/api/v1/reports/daily").param("date",DAY.plusDays(1).minusYears(3).minusDays(1).toString()),null,400);
    }

    @Test void schedulerUsesTheSameRecoverablePerUserTransaction() throws Exception {
        var a=actor();long todo=create(a,"주기 작업",DAY);NOW.set(NOON.plusSeconds(86400));
        var scheduler=new DailySettlementScheduler(users,settlements,policy);scheduler.settlePending();
        assertThat(jdbc.queryForObject("select next_settlement_date from user_daily_settlement_states where user_id=?",LocalDate.class,a.id)).isEqualTo(DAY.plusDays(1));
        assertThat(jdbc.queryForObject("select count(*) from todo_daily_snapshots where todo_id=?",Long.class,todo)).isEqualTo(1);
    }

    @Test void nextCandidatesExcludeAnotherAlreadyRunningTask() throws Exception {
        var a=actor();long completed=create(a,"바로 완료",DAY),running=create(a,"이미 실행 중",DAY),next=create(a,"다음 후보",DAY);
        body(a,post(path(running)+"/timer/start"),"{}",200);body(a,post(path(completed)+"/complete"),null,200);
        var summary=body(a,get(path(completed)+"/completion-summary"),null,200);
        assertThat(summary.path("nextTodos").size()).isEqualTo(1);assertThat(summary.path("nextTodos").get(0).path("id").asLong()).isEqualTo(next);
    }

    @Test void legacyCompletedTimestampOnPendingTaskIsNotCountedAsValidCompletion() throws Exception {
        var a=actor();long invalid=create(a,"기존 불일치",DAY);
        // 기존 DB는 상태와 완료 시각의 동치를 강제하지 않는다. 테스트 편의를 위한 새 제약은 넣지 않는다.
        jdbc.update("update todos set completed_at=? where id=?",java.sql.Timestamp.from(NOON),invalid);
        assertThat(daily(a,DAY).path("completedTodoCount").asLong()).isZero();
        var comparison=body(a,get("/api/v1/reports/time-comparisons").param("from",DAY.toString()).param("to",DAY.toString()),null,200);
        assertThat(comparison.path("totalElements").asLong()).isZero();body(a,get(path(invalid)+"/completion-summary"),null,409);
    }

    private Actor actor(){
        var u=users.saveAndFlush(new User("타이머검증"));
        var account=accounts.saveAndFlush(AuthAccount.local(u,UUID.randomUUID()+"@atch.invalid",encoder.encode("Timer123!")));
        String stamp=credentials.stamp(account);return new Actor(u.getId(),stamp,tokens.issueAccessToken(u.getId(),stamp));
    }
    private long create(Actor actor,String title,LocalDate day)throws Exception{
        return body(actor,post("/api/v1/todos"),"{\"title\":\""+title+"\",\"startDate\":\""+day+"\",\"startTime\":\"09:00\",\"dueDate\":\""+day+"\",\"dueTime\":\"10:00\",\"allDay\":false,\"userEstimatedDurationMinutes\":30}",201).path("id").asLong();
    }
    private JsonNode daily(Actor a,LocalDate day)throws Exception{return body(a,get("/api/v1/reports/daily").param("date",day.toString()),null,200);}
    private JsonNode body(Actor actor,MockHttpServletRequestBuilder request,String body,int status)throws Exception{
        request.header("Authorization","Bearer "+actor.token);if(body!=null)request.contentType("application/json").content(body);
        var response=mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as("%s HTTP status",request.buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI()).isEqualTo(status);
        return response.getContentAsString().isEmpty()?json.createObjectNode():json.readTree(response.getContentAsString());
    }
    private String path(long id){return "/api/v1/todos/"+id;}
    private String switchBody(long source,long target,long sid){return "{\"sourceTodoId\":"+source+",\"targetTodoId\":"+target+",\"sourceSessionId\":"+sid+"}";}
    private record Actor(long id,String stamp,String token){}
}
