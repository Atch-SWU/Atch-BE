package com.swu.atch.reward;

import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.auth.jwt.JwtTokenService;
import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.report.domain.*;
import com.swu.atch.report.repository.*;
import com.swu.atch.report.service.*;
import com.swu.atch.reward.domain.*;
import com.swu.atch.reward.repository.*;
import com.swu.atch.reward.service.CharacterService;
import com.swu.atch.reward.dto.request.EquipCharacterItemRequest;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import com.swu.atch.todo.domain.*;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.todo.service.TodoSchedulePolicy;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
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

@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class RewardIntegrationTest extends IsolatedAuthIntegrationTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 5);
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
    @Autowired AccountCredentials credentials;
    @Autowired JwtTokenService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired TodoRepository todos;
    @Autowired TodoDailySnapshotRepository snapshots;
    @Autowired UserDailySettlementStateRepository states;
    @Autowired CharacterItemRepository items;
    @Autowired UserCharacterItemRepository owned;
    @Autowired UserEquippedItemRepository equipped;
    @Autowired RewardSettlementRepository settlements;
    @Autowired RewardGrantRepository grants;
    @Autowired DailySettlementService daily;
    @Autowired CharacterService characters;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    private record Actor(long id, String stamp, String token) { }
    @BeforeEach void resetClock() { TIME.set(NOW); }

    @Test void newUserHasFourEmptySlotsAndNoInventedPastSettlement() throws Exception {
        var a = actor();
        var current = call(a, get("/api/v1/character"), null, 200);
        assertThat(current.path("slots").size()).isEqualTo(4);
        assertThat(current.path("slots").get(0).path("category").asText()).isEqualTo("COLOR");
        current.path("slots").forEach(s -> assertThat(s.path("item").isNull()).isTrue());
        assertThat(current.path("collection").path("allActiveItemsCollected").asBoolean()).isFalse();
        assertThat(call(a, get("/api/v1/character/items"), null, 200).path("items").isEmpty()).isTrue();
        call(a, get("/api/v1/rewards/settlements/" + DAY.minusDays(1)), null, 404);
        assertThat(settlements.count()).isZero();
    }

    @ParameterizedTest @CsvSource({"0,0,0", "4,1,0", "4,2,1", "4,3,2", "4,4,3"})
    void settlesExactGradesAndNoGrantDaysFromFinalSnapshots(int assigned, int completed, int count) throws Exception {
        var a = actor(); catalog(); history(a, DAY.minusDays(1), DAY, assigned, completed);
        var row = detail(a, DAY.minusDays(1));
        assertThat(row.path("assignedTodoCount").asInt()).isEqualTo(assigned);
        assertThat(row.path("completedTodoCount").asInt()).isEqualTo(completed);
        assertThat(row.path("eligibleGrades").size()).isEqualTo(count);
        assertThat(row.path("grants").size()).isEqualTo(count);
        assertThat(settlements.count()).isEqualTo(1);
        assertThat(grants.count()).isEqualTo(count); assertThat(owned.count()).isEqualTo(count);
        assertThat(detail(a, DAY.minusDays(1))).isEqualTo(row);
    }

    @Test void exhaustedCategoriesAndGradesNeverAccumulateRetroactiveRewardsForNewItems() throws Exception {
        var a = actor();
        var color = item(ItemGrade.B, ItemCategory.COLOR); var eyes = item(ItemGrade.B, ItemCategory.EYES);
        history(a, DAY.minusDays(3), DAY, 1, 1);
        var before = results(a, DAY.minusDays(3), DAY.minusDays(1));
        assertThat(settlements.count()).isEqualTo(3);
        assertThat(grants.count()).isEqualTo(2);
        assertThat(owned.findAllByUser_IdOrderByAcquiredAtDescIdDesc(a.id()))
                .extracting(x -> x.getCharacterItem().getId()).containsExactlyInAnyOrder(color.getId(), eyes.getId());
        assertThat(before.path("items").get(0).path("grants").isEmpty()).isTrue();
        assertThat(call(a, get("/api/v1/character"), null, 200).path("collection").path("allActiveItemsCollected").asBoolean()).isTrue();
        var added = item(ItemGrade.B, ItemCategory.BACKGROUND);
        assertThat(results(a, DAY.minusDays(3), DAY.minusDays(1)).path("items")).isEqualTo(before.path("items"));
        assertThat(grants.count()).isEqualTo(2);
        assertThat(call(a, get("/api/v1/character"), null, 200).path("collection").path("allActiveItemsCollected").asBoolean()).isFalse();
        todo(a, DAY, true);
        TIME.set(NOW.plusSeconds(86400));
        var next = detail(a, DAY);
        assertThat(next.path("grants").size()).isEqualTo(1);
        assertThat(next.path("grants").get(0).path("item").path("id").asLong()).isEqualTo(added.getId());
        assertThat(grants.count()).isEqualTo(3);
        assertThat(results(a, DAY.minusDays(3), DAY.minusDays(1)).path("items")).isEqualTo(before.path("items"));
    }

    @Test void inactiveOwnedItemsRemainUsableAndSamePutAndDeleteAreIdempotent() throws Exception {
        var a = actor(); var first = item(ItemGrade.B, ItemCategory.COLOR); var second = item(ItemGrade.A, ItemCategory.COLOR);
        own(a, first); own(a, second);
        jdbc.update("update character_items set active=false where id=?", first.getId()); em.clear();
        var put = put("/api/v1/character/equipment/COLOR");
        var initial = call(a, put, Map.of("characterItemId", first.getId()), 200);
        assertThat(initial.path("item").path("active").asBoolean()).isFalse();
        TIME.set(NOW.plusSeconds(60));
        assertThat(call(a, put("/api/v1/character/equipment/COLOR"), Map.of("characterItemId", first.getId()), 200)).isEqualTo(initial);
        var changed = call(a, put("/api/v1/character/equipment/COLOR"), Map.of("characterItemId", second.getId()), 200);
        assertThat(changed.path("equippedAt").asText()).isEqualTo(TIME.get().toString());
        assertThat(equipped.findAllByUser_IdOrderByCategoryAsc(a.id())).hasSize(1);
        call(a, delete("/api/v1/character/equipment/COLOR"), null, 204);
        call(a, delete("/api/v1/character/equipment/COLOR"), null, 204);
        assertThat(owned.count()).isEqualTo(2); assertThat(equipped.count()).isZero();
    }

    @Test void enforcesOwnershipCategoryAndValidationWithoutExposingAnotherUser() throws Exception {
        var a = actor(); var b = actor(); var color = item(ItemGrade.B, ItemCategory.COLOR); var eyes = item(ItemGrade.B, ItemCategory.EYES);
        own(a, color); own(b, eyes);
        call(a, put("/api/v1/character/equipment/EYES"), Map.of("characterItemId", eyes.getId()), 404);
        call(a, put("/api/v1/character/equipment/EYES"), Map.of("characterItemId", color.getId()), 400);
        call(a, put("/api/v1/character/equipment/COLOR"), Map.of("characterItemId", 999999), 404);
        call(a, put("/api/v1/character/equipment/COLOR"), Map.of("characterItemId", 0), 400);
        call(a, put("/api/v1/character/equipment/COLOR"), Map.of(), 400);
        call(a, delete("/api/v1/character/equipment/UNKNOWN"), null, 400);
        call(a, get("/api/v1/character/items").param("size", "101"), null, 400);
        call(a, get("/api/v1/character/items").param("grade", "X"), null, 400);
        call(a, get("/api/v1/rewards/settlements/" + DAY), null, 400);
        call(a, get("/api/v1/rewards/settlements/" + DAY.plusDays(1)), null, 400);
        call(a, get("/api/v1/rewards/settlements").param("from", DAY.toString()).param("to", DAY.minusDays(1).toString()), null, 400);
        call(a, get("/api/v1/rewards/settlements").param("from", DAY.minusDays(367).toString()).param("to", DAY.minusDays(1).toString()), null, 400);
        assertThat(equipped.count()).isZero();
    }

    @Test void filtersAndPagesOwnedItemsAndSettlements() throws Exception {
        var a = actor(); var first = item(ItemGrade.B, ItemCategory.COLOR); var second = item(ItemGrade.A, ItemCategory.EYES);
        own(a, first); own(a, second);
        var page = call(a, get("/api/v1/character/items").param("size", "1"), null, 200);
        assertThat(page.path("totalElements").asInt()).isEqualTo(2); assertThat(page.path("hasNext").asBoolean()).isTrue();
        var filtered = call(a, get("/api/v1/character/items").param("grade", "B").param("category", "COLOR"), null, 200);
        assertThat(filtered.path("totalElements").asInt()).isEqualTo(1);
        assertThat(filtered.path("items").get(0).path("item").path("id").asLong()).isEqualTo(first.getId());
        // 캐릭터 조회로 생긴 오늘의 추적 상태를 별도 과거 확정 fixture로 교체한다.
        states.deleteById(a.id()); states.flush();
        history(a, DAY.minusDays(2), DAY, 0, 0);
        var rewards = call(a, get("/api/v1/rewards/settlements").param("from", DAY.minusDays(2).toString())
                .param("to", DAY.minusDays(1).toString()).param("size", "1"), null, 200);
        assertThat(rewards.path("items").get(0).path("rewardDate").asText()).isEqualTo(DAY.minusDays(1).toString());
        assertThat(rewards.path("hasNext").asBoolean()).isTrue();
    }

    @Test void finalizedResultDoesNotChangeAfterTodoDeletionOrReopen() throws Exception {
        var a = actor(); catalog(); history(a, DAY.minusDays(1), DAY, 1, 1);
        var before = detail(a, DAY.minusDays(1));
        jdbc.update("update todos set status='PENDING', completed_at=null, deleted_at=? where user_id=?", java.sql.Timestamp.from(NOW), a.id());
        em.clear();
        assertThat(detail(a, DAY.minusDays(1))).isEqualTo(before);
    }

    @Test void schedulerFindsMissingRewardsEvenWhenSnapshotCursorIsAlreadyCurrent() {
        var a = actor(); history(a, DAY.minusDays(1), DAY, 0, 0);
        assertThat(users.findDailyMaintenanceCandidates(0L, DAY)).contains(a.id());
        new DailySettlementScheduler(users, daily, schedule).settlePending();
        assertThat(settlements.existsByUser_IdAndRewardDate(a.id(), DAY.minusDays(1))).isTrue();
        assertThat(users.findDailyMaintenanceCandidates(0L, DAY)).doesNotContain(a.id());
    }

    @Test void inconsistentSnapshotStopsRecoveryWithoutOverwritingIt() throws Exception {
        var a = actor(); history(a, DAY.minusDays(1), DAY, 1, 1);
        jdbc.update("update todo_daily_snapshots set snapshot_at=? where user_id=?", java.sql.Timestamp.from(NOW), a.id()); em.clear();
        call(a, get("/api/v1/rewards/settlements/" + DAY.minusDays(1)), null, 409);
        assertThat(settlements.count()).isZero();
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void longBacklogCommits31DayProgressAndNextRequestFinishes() throws Exception {
        var a = actor();
        try {
            new TransactionTemplate(transactions).execute(status -> { history(a, DAY.minusDays(32), DAY, 0, 0); return null; });
            call(a, get("/api/v1/character"), null, 503);
            assertThat(jdbc.queryForObject("select count(*) from reward_settlements where user_id=?", Long.class, a.id())).isEqualTo(31);
            call(a, get("/api/v1/character"), null, 200);
            assertThat(jdbc.queryForObject("select count(*) from reward_settlements where user_id=?", Long.class, a.id())).isEqualTo(32);
        } finally { cleanup(a); }
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedGrantRollsBackSnapshotSettlementOwnershipAndCursorThenRetries() throws Exception {
        var a = actor(); var catalog = catalog(); String trigger = "reward_fail_" + a.id();
        try {
            new TransactionTemplate(transactions).execute(status -> {
                states.saveAndFlush(new UserDailySettlementState(users.getReferenceById(a.id()), DAY.minusDays(1)));
                todo(a, DAY.minusDays(1), true); return null;
            });
            jdbc.execute("create function " + trigger + "() returns trigger language plpgsql as $$ begin if NEW.user_id=" + a.id()
                    + " then raise exception 'isolated reward failure'; end if; return NEW; end $$");
            jdbc.execute("create trigger " + trigger + " before insert on reward_grants for each row execute function " + trigger + "()");
            assertThat(call(a, get("/api/v1/character"), null, 503).path("code").asText()).isEqualTo("REWARD_SETTLEMENT_PENDING");
            for (String table : List.of("todo_daily_snapshots", "reward_settlements", "user_character_items", "reward_grants"))
                assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id=?", Long.class, a.id())).isZero();
            assertThat(jdbc.queryForObject("select next_settlement_date from user_daily_settlement_states where user_id=?", LocalDate.class, a.id())).isEqualTo(DAY.minusDays(1));
            jdbc.execute("drop trigger " + trigger + " on reward_grants"); jdbc.execute("drop function " + trigger + "()");
            call(a, get("/api/v1/character"), null, 200);
            assertThat(jdbc.queryForObject("select count(*) from reward_grants where user_id=?", Long.class, a.id())).isEqualTo(3);
            assertThat(jdbc.queryForObject("select count(*) from todo_daily_snapshots where user_id=?", Long.class, a.id())).isEqualTo(1);
        } finally {
            jdbc.execute("drop trigger if exists " + trigger + " on reward_grants"); jdbc.execute("drop function if exists " + trigger + "()");
            cleanup(a); catalog.forEach(i -> items.deleteById(i.getId()));
        }
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSettlementAndEquipmentProduceOnlyOneLogicalResult() throws Exception {
        var a = actor(); var catalog = new ArrayList<>(catalog()); var pool = Executors.newFixedThreadPool(2);
        try {
            new TransactionTemplate(transactions).execute(status -> { history(a, DAY.minusDays(1), DAY, 1, 1); return null; });
            var start = new CountDownLatch(1);
            Callable<Boolean> task = () -> { start.await(5, TimeUnit.SECONDS); return daily.prepare(a.id(), a.stamp()); };
            var first = pool.submit(task); var second = pool.submit(task); start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isTrue(); assertThat(second.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from reward_settlements where user_id=?", Long.class, a.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from reward_grants where user_id=?", Long.class, a.id())).isEqualTo(3);
            var equip1 = pool.submit(() -> characters.equip(a.id(), a.stamp(), ItemCategory.COLOR, new EquipCharacterItemRequest(catalog.get(0).getId())));
            var equip2 = pool.submit(() -> characters.equip(a.id(), a.stamp(), ItemCategory.COLOR, new EquipCharacterItemRequest(catalog.get(0).getId())));
            assertThat(equip1.get(15, TimeUnit.SECONDS)).isEqualTo(equip2.get(15, TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("select count(*) from user_equipped_items where user_id=?", Long.class, a.id())).isEqualTo(1);
            var another = item(ItemGrade.A, ItemCategory.COLOR); catalog.add(another); own(a, another);
            var swap1 = pool.submit(() -> characters.equip(a.id(), a.stamp(), ItemCategory.COLOR, new EquipCharacterItemRequest(catalog.get(0).getId())));
            var swap2 = pool.submit(() -> characters.equip(a.id(), a.stamp(), ItemCategory.COLOR, new EquipCharacterItemRequest(another.getId())));
            assertThat(swap1.get(15, TimeUnit.SECONDS).item().id()).isEqualTo(catalog.get(0).getId());
            assertThat(swap2.get(15, TimeUnit.SECONDS).item().id()).isEqualTo(another.getId());
            assertThat(jdbc.queryForObject("select character_item_id from user_equipped_items where user_id=?", Long.class, a.id()))
                    .isIn(catalog.get(0).getId(), another.getId());
            assertThat(jdbc.queryForObject("select count(*) from user_equipped_items where user_id=?", Long.class, a.id())).isEqualTo(1);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); cleanup(a); catalog.forEach(i -> items.deleteById(i.getId())); }
    }

    @Test void authenticationAndOpenApiExposeSixWorkingContractsWithoutGrantCommands() throws Exception {
        assertThat(mvc.perform(get("/api/v1/character")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/api/v1/rewards/settlements")).andReturn().getResponse().getStatus()).isEqualTo(401);
        var api = json.readTree(mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString());
        int count = 0;
        for (var path : api.path("paths").properties()) for (var operation : path.getValue().properties()) {
            if (operation.getValue().path("tags").toString().contains("캐릭터")) {
                count++; assertThat(operation.getValue().path("responses").has("501")).isFalse();
                assertThat(operation.getValue().path("security").toString()).contains("bearerAuth");
            }
        }
        assertThat(count).isEqualTo(6); assertThat(api.path("tags").size()).isEqualTo(8);
        assertThat(api.path("paths").toString()).doesNotContain("/rewards/claim", "/rewards/grant", "/rewards/settle\"");
        var out = java.nio.file.Path.of("build/verification/reward-complete-20261105/openapi.json");
        java.nio.file.Files.createDirectories(out.getParent()); java.nio.file.Files.writeString(out, api.toPrettyString());
    }

    private Actor actor() {
        var user = users.saveAndFlush(new User("보상검증"));
        var account = accounts.saveAndFlush(AuthAccount.local(user, UUID.randomUUID() + "@atch.invalid", encoder.encode("Reward123!")));
        var stamp = credentials.stamp(account); return new Actor(user.getId(), stamp, jwt.issueAccessToken(user.getId(), stamp));
    }
    private CharacterItem item(ItemGrade grade, ItemCategory category) {
        return items.saveAndFlush(CharacterItem.builder().name("검증아이템").grade(grade).category(category).assetKey("test/" + UUID.randomUUID()).build());
    }
    private List<CharacterItem> catalog() {
        return List.of(item(ItemGrade.B, ItemCategory.COLOR), item(ItemGrade.A, ItemCategory.EYES), item(ItemGrade.S, ItemCategory.ACCESSORY));
    }
    private void own(Actor a, CharacterItem item) { owned.saveAndFlush(UserCharacterItem.acquire(users.getReferenceById(a.id()), item)); }
    private Todo todo(Actor a, LocalDate date, boolean complete) {
        var start = date.atTime(9, 0).atZone(SEOUL).toInstant();
        var todo = Todo.builder().user(users.getReferenceById(a.id())).title("보상할일").allDay(false).startAt(start).dueAt(start.plusSeconds(3600))
                .currentScheduleDate(date).userEstimatedDurationMinutes(30).userEstimateSource(UserEstimateSource.USER_INPUT).selectedEstimateType(SelectedEstimateType.USER).build();
        if (complete) todo.complete(start.plusSeconds(1800));
        return todos.saveAndFlush(todo);
    }
    private void history(Actor a, LocalDate from, LocalDate until, int assigned, int completed) {
        var state = states.saveAndFlush(new UserDailySettlementState(users.getReferenceById(a.id()), from));
        for (var date = from; date.isBefore(until); date = date.plusDays(1)) {
            for (int i = 0; i < assigned; i++) {
                var todo = todo(a, date, i < completed);
                snapshots.saveAndFlush(TodoDailySnapshot.capture(todo, date, false, false, date.plusDays(1).atStartOfDay(SEOUL).toInstant()));
            }
            state.settle(date);
        }
        states.flush();
    }
    private JsonNode detail(Actor a, LocalDate date) throws Exception { return call(a, get("/api/v1/rewards/settlements/" + date), null, 200); }
    private JsonNode results(Actor a, LocalDate from, LocalDate to) throws Exception {
        return call(a, get("/api/v1/rewards/settlements").param("from", from.toString()).param("to", to.toString()), null, 200);
    }
    private JsonNode call(Actor a, MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        request.header("Authorization", "Bearer " + a.token());
        if (body != null) request.contentType("application/json").content(json.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        return response.getContentAsByteArray().length == 0 ? json.createObjectNode() : json.readTree(response.getContentAsString());
    }
    private void cleanup(Actor a) {
        for (String table : List.of("reward_grants", "reward_settlements", "user_equipped_items", "user_character_items", "todo_daily_snapshots", "todos", "auth_accounts"))
            jdbc.update("delete from " + table + " where user_id=?", a.id());
        jdbc.update("delete from users where id=?", a.id());
    }
}
