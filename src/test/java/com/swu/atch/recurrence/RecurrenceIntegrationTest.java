package com.swu.atch.recurrence;

import com.swu.atch.ai.dto.request.AiDraftRequest;
import com.swu.atch.ai.service.AiDraftService;
import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.jwt.JwtTokenService;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.*;
import com.swu.atch.recurrence.domain.*;
import com.swu.atch.recurrence.dto.request.*;
import com.swu.atch.recurrence.repository.*;
import com.swu.atch.recurrence.service.*;
import com.swu.atch.setting.repository.UserSettingRepository;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import com.swu.atch.todo.domain.*;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.todo.service.*;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import com.swu.atch.user.service.UserAccountService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@org.springframework.test.context.TestPropertySource(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class RecurrenceIntegrationTest extends IsolatedAuthIntegrationTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 29);
    private static final Instant NOW = DAY.atTime(12, 0).atZone(SEOUL).toInstant();
    private static final AtomicReference<Instant> TIME = new AtomicReference<>(NOW);
    @TestBean(name = "todoSchedulePolicy", methodName = "schedule", enforceOverride = true)
    private TodoSchedulePolicy schedule;
    static TodoSchedulePolicy schedule() {
        return new TodoSchedulePolicy(new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return TIME.get(); }
        });
    }
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    @Autowired AuthAccountRepository accounts;
    @Autowired TodoRepository todos;
    @Autowired RepeatPatternRepository patterns;
    @Autowired RecurrenceRuleRepository rules;
    @Autowired UserSettingRepository settings;
    @Autowired AccountCredentials credentials;
    @Autowired JwtTokenService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired RecurrenceGenerationService generation;
    @Autowired RecurrenceRuleService ruleService;
    @Autowired AiDraftService drafts;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired UserAccountService userAccounts;
    private record Actor(long id, String stamp, String token) { }
    @BeforeEach void resetClock() { TIME.set(NOW); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void detectsDailyAndAllDayWithoutCountingGetsOrDuplicateDates(boolean allDay) throws Exception {
        var a = actor(); observed(a, "독서", DAY.minusDays(1), allDay); observed(a, "독서", DAY, allDay);
        observed(a, "독서", DAY, allDay); observed(a, "독서", DAY.plusDays(1), allDay);
        var first = recommendations(a); var item = first.path("items").get(0);
        assertThat(first.path("totalElements").asInt()).isEqualTo(1);
        assertThat(item.path("frequency").asText()).isEqualTo("DAILY");
        assertThat(item.path("occurrenceCount").asInt()).isEqualTo(2);
        assertThat(item.path("allDay").asBoolean()).isEqualTo(allDay);
        assertThat(item.path("scheduledTime").isNull()).isEqualTo(allDay);
        assertThat(recommendations(a).path("items").get(0).path("updatedAt")).isEqualTo(item.path("updatedAt"));
        assertThat(rules.findAllByUser_IdAndDeletedAtIsNullOrderByIdDesc(a.id())).isEmpty();
    }

    @Test void distinguishesAllDayFromMidnightTimedAndUsesOriginalStartAfterRollover() throws Exception {
        var a = actor();
        var old = observed(a, "구분", DAY.minusDays(1), true); observed(a, "구분", DAY, true);
        jdbc.update("update todos set current_schedule_date=? where id=?", DAY, old.getId()); em.clear();
        var timed = observed(a, "구분", DAY, false);
        jdbc.update("update todos set start_at=? where id=?", java.sql.Timestamp.from(DAY.atStartOfDay(SEOUL).toInstant()), timed.getId()); em.clear();
        var items = recommendations(a).path("items");
        assertThat(items.size()).isEqualTo(1); assertThat(items.get(0).path("allDay").asBoolean()).isTrue();
    }

    @Test void weeklyRequiresSevenDaySpacingAndDoesNotCombineDifferentWeekdays() throws Exception {
        var a = actor(); observed(a, "주간", DAY.minusDays(7), false); observed(a, "주간", DAY, false);
        observed(a, "불규칙", DAY.minusDays(3), false); observed(a, "불규칙", DAY, false);
        var items = recommendations(a).path("items");
        assertThat(items.size()).isEqualTo(1); assertThat(items.get(0).path("frequency").asText()).isEqualTo("WEEKLY");
        assertThat(items.get(0).path("dayOfWeek").asText()).isEqualTo("THURSDAY");
    }

    @Test void skipResetsEvidenceAndStaleSkipCannotEraseNewSuggestion() throws Exception {
        var a = actor(); observed(a, "재추천", DAY.minusDays(1), false); observed(a, "재추천", DAY, false);
        var p = recommendations(a).path("items").get(0); var body = Map.<String,Object>of("expectedUpdatedAt", p.path("updatedAt").asText());
        var path = "/api/v1/recurrence-recommendations/" + p.path("patternId").asLong() + "/skip";
        assertThat(call(a, post(path), body, 200).path("occurrenceCount").asInt()).isZero();
        call(a, post(path), body, 200);
        assertThat(recommendations(a).path("items").isEmpty()).isTrue();
        observed(a, "재추천", DAY.plusDays(1), false); observed(a, "재추천", DAY.plusDays(2), false);
        TIME.set(NOW.plus(Duration.ofDays(2)));
        assertThat(recommendations(a).path("items").get(0).path("occurrenceCount").asInt()).isEqualTo(2);
        call(a, post(path), body, 409);
        assertThat(recommendations(a).path("items").get(0).path("occurrenceCount").asInt()).isEqualTo(2);
    }

    @Test void dismissalAndSettingsDoNotAffectRegisteredRules() throws Exception {
        var a = actor(); observed(a, "제외", DAY.minusDays(1), false); observed(a, "제외", DAY, false);
        var p = recommendations(a).path("items").get(0);
        var path = "/api/v1/recurrence-recommendations/" + p.path("patternId").asLong() + "/dismiss";
        var body = Map.<String,Object>of("expectedUpdatedAt", p.path("updatedAt").asText());
        call(a, post(path), body, 200); call(a, post(path), body, 200);
        assertThat(recommendations(a).path("items").isEmpty()).isTrue();
        var r = fixtureRule(a, "기존규칙", false, RecurrenceFrequency.DAILY, NOW.minusSeconds(86400));
        call(a, patch("/api/v1/settings/recurrence"), Map.of("repeatRecommendationEnabled", false), 200);
        assertThat(recommendations(a).path("enabled").asBoolean()).isFalse();
        assertThat(generation.prepare(a.id())).isEqualTo(1);
        assertThat(rules.findById(r.getId()).orElseThrow().isEnabled()).isTrue();
        call(a, post(path), body, 409);
        call(a, patch("/api/v1/settings/recurrence"), Map.of("repeatRecommendationEnabled", true), 200);
        assertThat(recommendations(a).path("items").isEmpty()).isTrue();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void registersOnceAndOnlyGeneratesOnNextEligibleDateWithFreshUnusedAiDraft(boolean allDay) throws Exception {
        var a = actor(); observed(a, "등록", DAY.minusDays(1), allDay); observed(a, "등록", DAY, allDay);
        var p = recommendations(a).path("items").get(0); var input = registration(p, allDay);
        var path = "/api/v1/recurrence-recommendations/" + p.path("patternId").asLong() + "/register";
        var created = call(a, post(path), input, 201); long id = created.path("id").asLong();
        assertThat(created.path("allDay").asBoolean()).isEqualTo(allDay);
        assertThat(call(a, post(path), input, 200).path("id").asLong()).isEqualTo(id);
        assertThat(generation.prepare(a.id())).isZero();
        assertThat(recommendations(a).path("items").isEmpty()).isTrue();
        TIME.set(NOW.plusSeconds(86400));
        assertThat(generation.prepare(a.id())).isEqualTo(1); assertThat(generation.prepare(a.id())).isZero();
        var t = todos.findAll().stream().filter(x -> Objects.equals(x.getRecurrenceRuleId(), id)).findFirst().orElseThrow();
        assertThat(t.getCurrentScheduleDate()).isEqualTo(DAY.plusDays(1));
        assertThat(t.getUserEstimateSource()).isEqualTo(UserEstimateSource.RECURRENCE_TEMPLATE);
        assertThat(t.getSelectedEstimateType()).isEqualTo(SelectedEstimateType.USER);
        assertThat(t.getAiEstimatedDurationMinutes()).isEqualTo(30);
        if (allDay) {
            assertThat(t.getStartAt()).isEqualTo(DAY.plusDays(1).atStartOfDay(SEOUL).toInstant());
            assertThat(t.getDueAt()).isEqualTo(DAY.plusDays(2).atStartOfDay(SEOUL).toInstant());
        }
        var draft = drafts.issue(a.id(), a.stamp(), new AiDraftRequest(t.getId(), null));
        assertThat(draft.timePredictionUsage().state().name()).isEqualTo("NOT_USED");
        assertThat(draft.decompositionUsage().state().name()).isEqualTo("NOT_APPLICABLE");
    }

    @Test void deletedOrEditedOccurrenceCannotBeRecreatedAndFutureCalendarDoesNotPrefill() throws Exception {
        var a = actor(); var r = fixtureRule(a, "회차", false, RecurrenceFrequency.WEEKLY, NOW.minusSeconds(86400));
        assertThat(generation.prepare(a.id())).isEqualTo(1);
        long id = jdbc.queryForObject("select id from todos where recurrence_rule_id=?", Long.class, r.getId());
        jdbc.update("update todos set start_at=start_at+interval '1 minute', deleted_at=? where id=?", java.sql.Timestamp.from(NOW), id);
        em.clear(); assertThat(generation.prepare(a.id())).isZero();
        var rule = rules.findById(r.getId()).orElseThrow();
        ruleService.update(a.id(), a.stamp(), rule.getId(), new RecurrenceRuleUpdateRequest("회차", null, DayOfWeek.FRIDAY,
                false, LocalTime.of(10,0), 60, 30, rule.getUpdatedAt()));
        TIME.set(NOW.plusSeconds(86400)); assertThat(generation.prepare(a.id())).isZero();
        var result = call(a, get("/api/v1/todos/calendar").param("from", DAY.plusDays(2).toString()).param("to", DAY.plusDays(30).toString()), null, 200);
        assertThat(result.path("dates").isEmpty()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from todos where recurrence_rule_id=?", Long.class, rule.getId())).isEqualTo(1);
    }

    @Test void reactivationSkipsPastStartAndOnlyUsesFutureStartToday() throws Exception {
        var a = actor(); var r = fixtureRule(a, "활성", false, RecurrenceFrequency.DAILY, NOW.minusSeconds(86400));
        var response = ruleService.enable(a.id(), a.stamp(), r.getId(), new RecurrenceRuleEnabledRequest(false,r.getUpdatedAt()));
        response = ruleService.enable(a.id(), a.stamp(), r.getId(), new RecurrenceRuleEnabledRequest(true,response.updatedAt()));
        assertThat(generation.prepare(a.id())).isZero();
        TIME.set(NOW.plusSeconds(86400)); assertThat(generation.prepare(a.id())).isEqualTo(1);
        var tomorrow = DAY.plusDays(1);
        assertThat(jdbc.queryForList("select recurrence_period_start_date from todos where recurrence_rule_id=?",LocalDate.class,r.getId())).containsExactly(tomorrow);
    }

    @Test void deletingRulePreservesTodosAndRequiresNewEvidenceForNewPattern() throws Exception {
        var a=actor(); observed(a,"다시등록",DAY.minusDays(1),true); observed(a,"다시등록",DAY,true);
        var p=recommendations(a).path("items").get(0);
        var r=call(a,post("/api/v1/recurrence-recommendations/"+p.path("patternId").asLong()+"/register"),registration(p,true),201);
        long id=r.path("id").asLong();
        call(a,delete("/api/v1/recurrence-rules/"+id),null,204); call(a,delete("/api/v1/recurrence-rules/"+id),null,204);
        assertThat(todos.findAll().stream().filter(t->t.getUser().getId().equals(a.id())).count()).isEqualTo(2);
        assertThat(recommendations(a).path("items").isEmpty()).isTrue();
        observed(a,"다시등록",DAY.plusDays(1),true); observed(a,"다시등록",DAY.plusDays(2),true); TIME.set(NOW.plusSeconds(172800));
        var again=recommendations(a).path("items").get(0);
        assertThat(again.path("patternId").asLong()).isNotEqualTo(p.path("patternId").asLong());
        call(a,post("/api/v1/recurrence-recommendations/"+p.path("patternId").asLong()+"/register"),registration(p,true),409);
    }

    @Test void updateConvertsScheduleWithoutChangingExistingTodoAndNoOpRetainsBoundary() throws Exception {
        var a=actor(); var r=fixtureRule(a,"변경",false,RecurrenceFrequency.DAILY,NOW.minusSeconds(86400));
        assertThat(generation.prepare(a.id())).isEqualTo(1);
        var before=jdbc.queryForObject("select to_jsonb(t)::text from todos t where recurrence_rule_id=?",String.class,r.getId());
        var changed=ruleService.update(a.id(),a.stamp(),r.getId(),new RecurrenceRuleUpdateRequest("변경",null,null,true,null,null,45,r.getUpdatedAt()));
        assertThat(changed.defaultDeadlineOffsetMinutes()).isEqualTo(1440);
        var repeat=ruleService.update(a.id(),a.stamp(),r.getId(),new RecurrenceRuleUpdateRequest("변경",null,null,true,null,1440,45,Instant.EPOCH));
        assertThat(repeat.updatedAt()).isEqualTo(changed.updatedAt());
        assertThat(repeat.generationNotBefore()).isEqualTo(changed.generationNotBefore());
        assertThat(jdbc.queryForObject("select to_jsonb(t)::text from todos t where recurrence_rule_id=?",String.class,r.getId())).isEqualTo(before);
        TIME.set(NOW.plusSeconds(86400)); assertThat(generation.prepare(a.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select all_day from todos where recurrence_rule_id=? and recurrence_period_start_date=?",Boolean.class,r.getId(),DAY.plusDays(1))).isTrue();
    }

    @ParameterizedTest @ValueSource(strings={"get","put","enabled","delete"})
    void hidesForeignRule(String method) throws Exception {
        var a=actor(); var other=actor(); var r=fixtureRule(a,"소유",true,RecurrenceFrequency.DAILY,NOW);
        String path="/api/v1/recurrence-rules/"+r.getId();
        switch(method){
            case "get" -> call(other,get(path),null,404);
            case "delete" -> call(other,delete(path),null,404);
            case "enabled" -> call(other,patch(path+"/enabled"),Map.of("enabled",false,"expectedUpdatedAt",r.getUpdatedAt().toString()),404);
            default -> call(other,put(path),edit(r),404);
        }
    }

    @Test void rejectsInvalidAllDayAndStaleUpdatesAndPreservesOtherSettings() throws Exception {
        var a=actor(); var r=fixtureRule(a,"입력",false,RecurrenceFrequency.DAILY,NOW);
        var body=edit(r); body.put("allDay",true); body.put("scheduledTime",null); body.put("defaultDeadlineOffsetMinutes",60);
        call(a,put("/api/v1/recurrence-rules/"+r.getId()),body,400);
        body=edit(r); body.put("taskName","새이름"); body.put("expectedUpdatedAt",Instant.EPOCH.toString());
        call(a,put("/api/v1/recurrence-rules/"+r.getId()),body,409);
        call(a,patch("/api/v1/settings/recurrence"),Map.of(),400);
        call(a,get("/api/v1/recurrence-rules").param("size","101"),null,400);
        call(a,get("/api/v1/recurrence-recommendations").param("page","-1"),null,400);
        call(a,patch("/api/v1/settings/timer"),Map.of("taskSwitchWaitMinutes",7),200);
        call(a,patch("/api/v1/settings/recurrence"),Map.of("repeatRecommendationEnabled",false),200);
        assertThat(call(a,get("/api/v1/settings/timer"),null,200).path("taskSwitchWaitMinutes").asInt()).isEqualTo(7);
    }

    @Test void currentTodoReadPreparesTodayBeforeQueryAndPreservesRecurrenceSource() throws Exception {
        var a=actor(); var r=fixtureRule(a,"조회복구",true,RecurrenceFrequency.DAILY,NOW.minusSeconds(86400));
        var page=call(a,get("/api/v1/todos"),null,200);
        assertThat(page.path("totalElements").asInt()).isEqualTo(1);
        assertThat(page.path("items").get(0).path("recurrenceRuleId").asLong()).isEqualTo(r.getId());
        assertThat(recommendations(a).path("items").isEmpty()).isTrue();
    }

    @Test void authenticationAndOpenApiExposeFrontendContractsWithoutInternalRoutes() throws Exception {
        assertThat(mvc.perform(get("/api/v1/recurrence-rules")).andReturn().getResponse().getStatus()).isEqualTo(401);
        var response=mvc.perform(get("/v3/api-docs")).andReturn().getResponse(); assertThat(response.getStatus()).isEqualTo(200);
        var api=json.readTree(response.getContentAsString());
        assertThat(api.path("paths").path("/api/v1/recurrence-rules/{ruleId}").has("put")).isTrue();
        assertThat(api.path("components").path("schemas").path("RecurrenceRuleUpdateRequest").path("properties").has("allDay")).isTrue();
        assertThat(api.path("components").path("schemas").path("RecurrenceRuleResponse").path("properties").path("allDay").path("description").asText()).contains("하루종일");
        assertThat(api.path("paths").toString()).doesNotContain("/recurrence/generate","/recurrence/detect");
        var out=java.nio.file.Path.of("build/verification/recurrence-20261029/openapi.json"); java.nio.file.Files.createDirectories(out.getParent()); java.nio.file.Files.writeString(out,api.toPrettyString());
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentGenerationCommitsOneOccurrenceAndAccountDeletionRemovesAllOwnedData() throws Exception {
        var a=actor(); var r=fixtureRule(a,"동시",true,RecurrenceFrequency.DAILY,NOW.minusSeconds(86400));
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            Callable<Integer> run=()->{start.await(5,TimeUnit.SECONDS); return generation.prepare(a.id());};
            var one=pool.submit(run); var two=pool.submit(run); start.countDown();
            assertThat(one.get(15,TimeUnit.SECONDS)+two.get(15,TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from todos where recurrence_rule_id=?",Long.class,r.getId())).isEqualTo(1);
            userAccounts.withdraw(a.id(),a.stamp(),"Recurrence123!");
            for(var table:List.of("todos","recurrence_rules","repeat_patterns","ai_drafts"))
                assertThat(jdbc.queryForObject("select count(*) from "+table+" where user_id=?",Long.class,a.id())).isZero();
        } finally {start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void statelessHttpRoundTripsPreserveVersionAndRegistrationIdempotency() throws Exception {
        var a=actor(); observed(a,"분리요청",DAY.minusDays(1),true); observed(a,"분리요청",DAY,true);
        var item=recommendations(a).path("items").get(0);
        var path="/api/v1/recurrence-recommendations/"+item.path("patternId").asLong()+"/register";
        var first=call(a,post(path),registration(item,true),201);
        assertThat(call(a,post(path),registration(item,true),200).path("updatedAt")).isEqualTo(first.path("updatedAt"));
        var changed=call(a,patch("/api/v1/recurrence-rules/"+first.path("id").asLong()+"/enabled"),
                Map.of("enabled",false,"expectedUpdatedAt",first.path("updatedAt").asText()),200);
        assertThat(changed.path("enabled").asBoolean()).isFalse();
    }

    @Test void reactivationBeforeFutureStartCanGenerateTodayButAllDayCannot() {
        var a=actor(); var timed=fixtureRule(a,"오후",false,RecurrenceFrequency.DAILY,NOW.minusSeconds(86400));
        timed.updateDetails("오후",null,null,LocalTime.of(18,0),false,60,30,NOW.minusSeconds(3600)); rules.flush();
        var off=ruleService.enable(a.id(),a.stamp(),timed.getId(),new RecurrenceRuleEnabledRequest(false,timed.getUpdatedAt()));
        ruleService.enable(a.id(),a.stamp(),timed.getId(),new RecurrenceRuleEnabledRequest(true,off.updatedAt()));
        var allDay=fixtureRule(a,"종일활성",true,RecurrenceFrequency.DAILY,NOW.minusSeconds(86400));
        var offDay=ruleService.enable(a.id(),a.stamp(),allDay.getId(),new RecurrenceRuleEnabledRequest(false,allDay.getUpdatedAt()));
        ruleService.enable(a.id(),a.stamp(),allDay.getId(),new RecurrenceRuleEnabledRequest(true,offDay.updatedAt()));
        assertThat(generation.prepare(a.id())).isEqualTo(1);
        assertThat(jdbc.queryForList("select recurrence_rule_id from todos where user_id=?",Long.class,a.id())).containsExactly(timed.getId());
    }

    @Test void profileIsFreshButTemplateEstimateDoesNotBecomeLearningObservation() {
        var a=actor(); var t=observed(a,"직접관측",DAY.minusDays(1),false);
        var session=com.swu.atch.session.domain.Session.start(t,NOW.minusSeconds(3600));
        session.finish(NOW,com.swu.atch.session.domain.SessionEndReason.COMPLETED);
        em.persist(session); t.complete(NOW); todos.flush();
        var r=fixtureRule(a,"새통계",false,RecurrenceFrequency.DAILY,NOW.minusSeconds(86400));
        assertThat(generation.prepare(a.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select ai_estimated_duration_minutes from todos where recurrence_rule_id=?",Integer.class,r.getId())).isEqualTo(36);
        assertThat(jdbc.queryForObject("select sample_count from time_estimation_profiles where user_id=?",Integer.class,a.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ai_operations o join ai_drafts d on d.id=o.draft_id where d.user_id=?",Long.class,a.id())).isZero();
    }

    @Test void overlapAndRegistrationVersionsAreEnforced() throws Exception {
        var a=actor(); observed(a,"겹침",DAY.minusDays(1),false); observed(a,"겹침",DAY,false);
        var item=recommendations(a).path("items").get(0); var body=registration(item,false); body.put("expectedUpdatedAt",Instant.EPOCH.toString());
        call(a,post("/api/v1/recurrence-recommendations/"+item.path("patternId").asLong()+"/register"),body,409);
        var r=fixtureRule(a,"겹침",false,RecurrenceFrequency.WEEKLY,NOW);
        assertThat(recommendations(a).path("items").isEmpty()).isTrue();
        var other=fixtureRule(a,"수정전",false,RecurrenceFrequency.DAILY,NOW);
        var edit=edit(other); edit.put("taskName","겹침");
        assertThat(call(a,put("/api/v1/recurrence-rules/"+other.getId()),edit,409).path("code").asText()).isEqualTo("RECURRENCE_RULE_OVERLAP");
    }

    @ParameterizedTest @ValueSource(strings={"pattern-time","rule-time","negative-count","negative-boundary"})
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void databaseRejectsInvalidAllDayAndResetState(String kind) {
        var a=actor(); var r=fixtureRule(a,"DB제약",false,RecurrenceFrequency.DAILY,NOW);
        long pattern=jdbc.queryForObject("select source_pattern_id from recurrence_rules where id=?",Long.class,r.getId());
        String sql=switch(kind){
            case "pattern-time" -> "update repeat_patterns set all_day=true where id="+pattern;
            case "rule-time" -> "update recurrence_rules set all_day=true,scheduled_time=null,default_deadline_offset_minutes=60 where id="+r.getId();
            case "negative-count" -> "update repeat_patterns set occurrence_count=-1 where id="+pattern;
            default -> "update repeat_patterns set detection_after_todo_id=-1 where id="+pattern;
        };
        assertThatThrownBy(()->jdbc.update(sql)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("update repeat_patterns set occurrence_count=0 where id=?",pattern);
        assertThat(jdbc.queryForObject("select occurrence_count from repeat_patterns where id=?",Integer.class,pattern)).isZero();
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void draftFailureRollsBackGeneratedTodoAndCanRetryWithoutPartialData() {
        var a=actor(); var r=fixtureRule(a,"롤백",true,RecurrenceFrequency.DAILY,NOW.minusSeconds(86400));
        String name="recurrence_test_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("create function "+name+"() returns trigger language plpgsql as $$ begin if new.user_id="+a.id()+" then raise exception 'synthetic rollback' using errcode='23514'; end if; return new; end $$");
        try {
            jdbc.execute("create trigger "+name+" before insert on ai_drafts for each row execute function "+name+"()");
            assertThatThrownBy(()->generation.prepare(a.id())).isInstanceOfSatisfying(BusinessException.class,
                    e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RECURRENCE_PREPARATION_PENDING));
            assertThat(jdbc.queryForObject("select count(*) from todos where recurrence_rule_id=?",Long.class,r.getId())).isZero();
        } finally {
            jdbc.execute("drop trigger if exists "+name+" on ai_drafts"); jdbc.execute("drop function "+name+"()");
        }
        assertThat(generation.prepare(a.id())).isEqualTo(1); assertThat(generation.prepare(a.id())).isZero();
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void disabledCommitWinsBeforeConcurrentGenerator() throws Exception {
        var a=actor(); var r=fixtureRule(a,"비활성경합",false,RecurrenceFrequency.DAILY,NOW.minusSeconds(86400));
        var lock=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var disable=pool.submit(()->new TransactionTemplate(transactions).execute(status->{
                users.findByIdForUpdate(a.id()).orElseThrow();
                ruleService.enable(a.id(),a.stamp(),r.getId(),new RecurrenceRuleEnabledRequest(false,r.getUpdatedAt())); lock.countDown();
                try {if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("fixture timeout");}
                catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}return true;
            }));
            assertThat(lock.await(10,TimeUnit.SECONDS)).isTrue();
            var create=pool.submit(()->{started.countDown();return generation.prepare(a.id());});
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();release.countDown();
            assertThat(disable.get(15,TimeUnit.SECONDS)).isTrue();assertThat(create.get(15,TimeUnit.SECONDS)).isZero();
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void settlementBacklogMakesProgressBeforeTodayOnlyGeneration() {
        var a=actor();var r=fixtureRule(a,"누락복구",true,RecurrenceFrequency.DAILY,NOW.minusSeconds(86400));
        var old=DAY.minusDays(90);
        jdbc.update("insert into user_daily_settlement_states(user_id,tracking_started_on,next_settlement_date) values(?,?,?)",a.id(),old,old);
        for(int attempt=0;attempt<2;attempt++) {
            assertThatThrownBy(()->generation.prepare(a.id())).isInstanceOfSatisfying(BusinessException.class,
                    e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DAILY_SETTLEMENT_PENDING));
        }
        assertThat(jdbc.queryForObject("select next_settlement_date from user_daily_settlement_states where user_id=?",LocalDate.class,a.id())).isEqualTo(old.plusDays(62));
        assertThat(generation.prepare(a.id())).isEqualTo(1);
        assertThat(jdbc.queryForList("select recurrence_period_start_date from todos where recurrence_rule_id=?",LocalDate.class,r.getId())).containsExactly(DAY);
    }

    @Test void deletingRuleKeepsAlreadyGeneratedTodoAndOriginalRegisteredPattern() throws Exception {
        var a=actor();var r=fixtureRule(a,"삭제보존",true,RecurrenceFrequency.DAILY,NOW.minusSeconds(86400));
        assertThat(generation.prepare(a.id())).isEqualTo(1);
        var before=jdbc.queryForObject("select to_jsonb(t)::text from todos t where recurrence_rule_id=?",String.class,r.getId());
        call(a,delete("/api/v1/recurrence-rules/"+r.getId()),null,204);
        assertThat(generation.prepare(a.id())).isZero();
        assertThat(jdbc.queryForObject("select to_jsonb(t)::text from todos t where recurrence_rule_id=?",String.class,r.getId())).isEqualTo(before);
        assertThat(jdbc.queryForObject("select status from repeat_patterns where id=?",String.class,r.getSourcePattern().getId())).isEqualTo("REGISTERED");
        call(a,get("/api/v1/recurrence-rules/"+r.getId()),null,404);
    }

    @Test void rulesAreFilteredBeforeDatabasePagination() throws Exception {
        var a=actor();var first=fixtureRule(a,"목록첫째",false,RecurrenceFrequency.DAILY,NOW);
        var second=fixtureRule(a,"목록둘째",true,RecurrenceFrequency.DAILY,NOW);
        ruleService.enable(a.id(),a.stamp(),first.getId(),new RecurrenceRuleEnabledRequest(false,first.getUpdatedAt()));
        var filtered=call(a,get("/api/v1/recurrence-rules").param("enabled","true").param("size","1"),null,200);
        assertThat(filtered.path("totalElements").asInt()).isEqualTo(1);
        assertThat(filtered.path("items").get(0).path("id").asLong()).isEqualTo(second.getId());
        assertThat(filtered.path("hasNext").asBoolean()).isFalse();
        var all=call(a,get("/api/v1/recurrence-rules").param("size","1"),null,200);
        assertThat(all.path("totalElements").asInt()).isEqualTo(2);assertThat(all.path("hasNext").asBoolean()).isTrue();
    }

    private Actor actor() {
        var u=users.saveAndFlush(new User("반복검증"));
        var account=accounts.saveAndFlush(AuthAccount.local(u,UUID.randomUUID()+"@atch.invalid",encoder.encode("Recurrence123!")));
        var stamp=credentials.stamp(account); return new Actor(u.getId(),stamp,jwt.issueAccessToken(u.getId(),stamp));
    }
    private Todo observed(Actor a,String title,LocalDate date,boolean allDay) {
        var start=(allDay?date.atStartOfDay(SEOUL):date.atTime(9,0).atZone(SEOUL)).toInstant();
        return todos.saveAndFlush(Todo.builder().user(users.getReferenceById(a.id())).title(title).allDay(allDay)
                .startAt(start).dueAt(allDay?date.plusDays(1).atStartOfDay(SEOUL).toInstant():start.plusSeconds(3600))
                .currentScheduleDate(date).userEstimatedDurationMinutes(30).userEstimateSource(UserEstimateSource.USER_INPUT)
                .selectedEstimateType(SelectedEstimateType.USER).build());
    }
    private RecurrenceRule fixtureRule(Actor a,String name,boolean allDay,RecurrenceFrequency frequency,Instant boundary) {
        return new TransactionTemplate(transactions).execute(status->{
            var u=users.getReferenceById(a.id());
            var p=RepeatPattern.builder().user(u).taskName(name).frequency(frequency).dayOfWeek(frequency==RecurrenceFrequency.WEEKLY?DAY.getDayOfWeek():null)
                    .allDay(allDay).scheduledTime(allDay?null:LocalTime.of(9,0)).occurrenceCount(2).lastOccurrenceDate(DAY.minusDays(1)).build();
            p.observe(2,p.getLastOccurrenceDate(),true,NOW); p.register(); patterns.saveAndFlush(p);
            return rules.saveAndFlush(RecurrenceRule.builder().user(u).sourcePattern(p).taskName(name).dayOfWeek(p.getDayOfWeek()).scheduledTime(p.getScheduledTime())
                    .defaultDeadlineOffsetMinutes(allDay?1440:60).defaultUserEstimatedDurationMinutes(30).generationNotBefore(boundary).build());
        });
    }
    private JsonNode recommendations(Actor a)throws Exception {return call(a,get("/api/v1/recurrence-recommendations"),null,200);}
    private Map<String,Object> registration(JsonNode p,boolean allDay){
        var b=new LinkedHashMap<String,Object>();b.put("memo",null);b.put("defaultDeadlineOffsetMinutes",allDay?null:60);
        b.put("defaultUserEstimatedDurationMinutes",30);b.put("expectedUpdatedAt",p.path("updatedAt").asText());return b;
    }
    private Map<String,Object> edit(RecurrenceRule r){
        var b=new LinkedHashMap<String,Object>();b.put("taskName",r.getTaskName());b.put("memo",r.getMemo());b.put("allDay",r.isAllDay());
        b.put("dayOfWeek",r.getDayOfWeek()==null?null:r.getDayOfWeek().name());b.put("scheduledTime",r.getScheduledTime()==null?null:r.getScheduledTime().toString());
        b.put("defaultDeadlineOffsetMinutes",r.getDefaultDeadlineOffsetMinutes());b.put("defaultUserEstimatedDurationMinutes",r.getDefaultUserEstimatedDurationMinutes());
        b.put("expectedUpdatedAt",r.getUpdatedAt().toString());return b;
    }
    private JsonNode call(Actor a,MockHttpServletRequestBuilder req,Map<String,?> input,int status)throws Exception {
        req.header("Authorization","Bearer "+a.token());if(input!=null)req.contentType("application/json").content(json.writeValueAsString(input));
        var result=mvc.perform(req).andReturn().getResponse();assertThat(result.getStatus()).as(result.getContentAsString()).isEqualTo(status);
        return result.getContentAsString().isBlank()?json.nullNode():json.readTree(result.getContentAsString());
    }
}
