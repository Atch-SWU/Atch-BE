package com.swu.atch.todo.repository;

import com.swu.atch.AtchApplication;
import com.swu.atch.ai.domain.PredictionReasonCode;
import com.swu.atch.ai.domain.PredictionStrategy;
import com.swu.atch.ai.domain.TimeEstimationProfile;
import com.swu.atch.ai.domain.TimePredictionExperiment;
import com.swu.atch.ai.domain.TodoEmbedding;
import com.swu.atch.ai.repository.TimeEstimationProfileRepository;
import com.swu.atch.ai.repository.TimePredictionExperimentRepository;
import com.swu.atch.ai.repository.TodoEmbeddingRepository;
import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.domain.AuthProvider;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.recurrence.domain.RecurrenceFrequency;
import com.swu.atch.recurrence.domain.RecurrenceRule;
import com.swu.atch.recurrence.domain.RepeatPattern;
import com.swu.atch.recurrence.domain.RepeatPatternStatus;
import com.swu.atch.recurrence.repository.RecurrenceRuleRepository;
import com.swu.atch.recurrence.repository.RepeatPatternRepository;
import com.swu.atch.reward.domain.CharacterItem;
import com.swu.atch.reward.domain.ItemGrade;
import com.swu.atch.reward.domain.ItemCategory;
import com.swu.atch.reward.domain.UserCharacterItem;
import com.swu.atch.reward.domain.UserEquippedItem;
import com.swu.atch.reward.domain.RewardSettlement;
import com.swu.atch.reward.domain.RewardGrant;
import com.swu.atch.reward.repository.CharacterItemRepository;
import com.swu.atch.reward.repository.UserCharacterItemRepository;
import com.swu.atch.reward.repository.UserEquippedItemRepository;
import com.swu.atch.reward.repository.RewardSettlementRepository;
import com.swu.atch.reward.repository.RewardGrantRepository;
import com.swu.atch.setting.domain.UserSetting;
import com.swu.atch.setting.repository.UserSettingRepository;
import com.swu.atch.user.repository.UserRepository;
import com.swu.atch.report.domain.TodoDailySnapshot;
import com.swu.atch.report.repository.TodoDailySnapshotRepository;
import com.swu.atch.session.domain.Session;
import com.swu.atch.session.domain.SessionEndReason;
import com.swu.atch.session.repository.SessionRepository;
import com.swu.atch.todo.domain.SelectedEstimateType;
import com.swu.atch.todo.domain.Todo;
import com.swu.atch.todo.domain.TodoStatus;
import com.swu.atch.todo.domain.UserEstimateSource;
import com.swu.atch.user.domain.User;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(
        showSql = false,
        properties = "spring.config.location=classpath:/application-todo-test.properties"
)
@ActiveProfiles("todo-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ContextConfiguration(classes = TodoRepositoryTest.JpaConfiguration.class)
@Testcontainers
class TodoRepositoryTest {

    private static final LocalDate SCHEDULE_DATE = LocalDate.of(2026, 10, 22);
    private static final Instant START = Instant.parse("2026-10-21T15:00:00Z");
    private static final Instant DUE = Instant.parse("2026-10-22T15:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("atch_todo_test")
            .withLabel("atch.verification", "v11").withReuse(false);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    // 메인 애플리케이션의 인증·Redis 설정을 스캔하지 않음
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = AtchApplication.class)
    @EnableJpaRepositories(basePackageClasses = {
            TodoRepository.class,
            SessionRepository.class,
            TodoDailySnapshotRepository.class,
            TimeEstimationProfileRepository.class,
            AuthAccountRepository.class,
            UserSettingRepository.class,
            UserRepository.class,
            RepeatPatternRepository.class,
            CharacterItemRepository.class
    })
    static class JpaConfiguration {

        @Bean
        @FlywayDataSource
        DataSource dataSource() {
            // 환경변수나 properties의 DB 주소를 사용하지 않음
            return new DriverManagerDataSource(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
            );
        }

        @Bean
        FlywayMigrationStrategy isolatedMigration(DataSource dataSource) {
            return flyway -> {
                // 외부 DataSource로 바뀌면 SQL 실행 전에 중단
                if (flyway.getConfiguration().getDataSource() != dataSource
                        || !flyway.getConfiguration().isCleanDisabled()) {
                    throw new IllegalStateException("Flyway 테스트 DataSource가 일치하지 않습니다.");
                }
                flyway.migrate();
                flyway.validate();
            };
        }
    }

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private TodoDailySnapshotRepository snapshotRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Autowired private TimeEstimationProfileRepository profileRepository;
    @Autowired private TodoEmbeddingRepository embeddingRepository;
    @Autowired private TimePredictionExperimentRepository experimentRepository;
    @Autowired private AuthAccountRepository authRepository;
    @Autowired private UserSettingRepository settingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RepeatPatternRepository patternRepository;
    @Autowired private RecurrenceRuleRepository ruleRepository;
    @Autowired private CharacterItemRepository itemRepository;
    @Autowired private UserCharacterItemRepository ownedRepository;
    @Autowired private UserEquippedItemRepository equippedRepository;
    @Autowired private RewardSettlementRepository settlementRepository;
    @Autowired private RewardGrantRepository grantRepository;

    @Test
    void migratesV1ThroughV14AndValidatesJpaOnTheContainer() throws SQLException {
        assertThat(flyway.getConfiguration().getDataSource()).isSameAs(dataSource);
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(POSTGRES.getJdbcUrl());
            assertThat(connection.getCatalog()).isEqualTo("atch_todo_test");
        }
        assertThat(Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(Object::toString).toList()).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank", String.class))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM flyway_schema_history WHERE success AND checksum IS NOT NULL
                """, Long.class)).isEqualTo(14L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Long.class
        )).isEqualTo(1L);
        assertThat(entityManager.getEntityManagerFactory().getProperties())
                .containsEntry("hibernate.hbm2ddl.auto", "validate");
        System.out.println("V14 JPA: database=atch_todo_test; container=" + POSTGRES.getContainerId()
                + "; PostgreSQL=" + jdbc.queryForObject("SHOW server_version", String.class)
                + "; vector=" + jdbc.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname='vector'", String.class));
    }

    @Test
    void savesAndReloadsTodoMappings() {
        User owner = persistUser("투두사용자");
        Todo saved = todoRepository.saveAndFlush(Todo.builder()
                .user(owner)
                .title("하루종일 작업")
                .sourceTaskName("원본 작업")
                .memo("첫 번째 줄\n두 번째 줄")
                .startAt(START)
                .dueAt(DUE)
                .currentScheduleDate(SCHEDULE_DATE)
                .allDay(true)
                .userEstimatedDurationMinutes(30)
                .userEstimateSource(UserEstimateSource.USER_INPUT)
                .aiEstimatedDurationMinutes(45)
                .selectedEstimateType(SelectedEstimateType.AI)
                .build());
        entityManager.clear();

        Todo found = todoRepository.findByIdAndUser_IdAndDeletedAtIsNull(
                saved.getId(), owner.getId()
        ).orElseThrow();

        assertThat(found.getUser().getId()).isEqualTo(owner.getId());
        assertThat(found.getTitle()).isEqualTo("하루종일 작업");
        assertThat(found.getSourceTaskName()).isEqualTo("원본 작업");
        assertThat(found.getMemo()).isEqualTo("첫 번째 줄\n두 번째 줄");
        assertThat(found.getStartAt()).isEqualTo(START);
        assertThat(found.getDueAt()).isEqualTo(DUE);
        assertThat(found.getCurrentScheduleDate()).isEqualTo(SCHEDULE_DATE);
        assertThat(found.isAllDay()).isTrue();
        assertThat(found.getUserEstimatedDurationMinutes()).isEqualTo(30);
        assertThat(found.getAiEstimatedDurationMinutes()).isEqualTo(45);
        assertThat(found.getUserEstimateSource()).isEqualTo(UserEstimateSource.USER_INPUT);
        assertThat(found.getSelectedEstimateType()).isEqualTo(SelectedEstimateType.AI);
        assertThat(found.getStatus()).isEqualTo(TodoStatus.PENDING);
        assertThat(found.getRecurrenceRuleId()).isNull();
        assertThat(found.getRecurrenceOccurrenceAt()).isNull();
        assertThat(found.getCompletedAt()).isNull();
        assertThat(found.getDeletedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();

        // 이월 동작 구현 없이 배정 날짜 변경을 DB가 허용하는지 확인
        jdbc.update("UPDATE todos SET current_schedule_date = ? WHERE id = ?",
                SCHEDULE_DATE.plusDays(1), found.getId());
        entityManager.clear();
        Todo rolled = todoRepository.findByIdAndUser_IdAndDeletedAtIsNull(
                found.getId(), owner.getId()
        ).orElseThrow();
        assertThat(rolled.getCurrentScheduleDate()).isEqualTo(SCHEDULE_DATE.plusDays(1));
        assertThat(rolled.getStartAt()).isEqualTo(START);
        assertThat(rolled.getDueAt()).isEqualTo(DUE);
    }

    @Test
    void queriesOnlyTheOwnersNonDeletedTodos() {
        User owner = persistUser("소유사용자");
        User other = persistUser("다른사용자");
        Todo active = persistTodo(owner, null);
        Todo deleted = persistTodo(owner, null);
        persistTodo(other, null);
        jdbc.update("UPDATE todos SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                deleted.getId());
        entityManager.clear();

        assertThat(todoRepository.findByIdAndUser_IdAndDeletedAtIsNull(
                active.getId(), other.getId())).isEmpty();
        assertThat(todoRepository.findByIdAndUser_IdAndDeletedAtIsNull(
                deleted.getId(), owner.getId())).isEmpty();
        assertThat(todoRepository
                .findAllByUser_IdAndCurrentScheduleDateAndDeletedAtIsNullOrderByStartAtAscIdAsc(
                        owner.getId(), SCHEDULE_DATE))
                .extracting(Todo::getId).containsExactly(active.getId());
        var page = todoRepository
                .findAllByUser_IdAndCurrentScheduleDateBetweenAndDeletedAtIsNull(
                        owner.getId(), SCHEDULE_DATE, SCHEDULE_DATE, PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Todo::getId).containsExactly(active.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(todoRepository
                .findAllByUser_IdAndStatusAndDeletedAtIsNullOrderByStartAtAscIdAsc(
                        owner.getId(), TodoStatus.PENDING))
                .extracting(Todo::getId).containsExactly(active.getId());
        assertThat(todoRepository.existsByUser_IdAndCurrentScheduleDateAndDeletedAtIsNull(
                owner.getId(), SCHEDULE_DATE)).isTrue();

        jdbc.update("UPDATE todos SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                active.getId());
        assertThat(todoRepository.existsByUser_IdAndCurrentScheduleDateAndDeletedAtIsNull(
                owner.getId(), SCHEDULE_DATE)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsDuplicateOccurrencesEvenAfterSoftDeletion(boolean deleted) {
        User owner = persistUser("반복사용자");
        RecurrenceRule rule = persistRule(owner, persistPattern(owner, RecurrenceFrequency.DAILY));
        Todo first = persistTodo(owner, rule.getId());
        if (deleted) {
            jdbc.update("UPDATE todos SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                    first.getId());
        }
        assertViolation(() -> jdbc.update("""
                INSERT INTO todos (
                    user_id, recurrence_rule_id, recurrence_occurrence_at, recurrence_period_start_date, title,
                    start_at, due_at, current_schedule_date, all_day,
                    user_estimated_duration_minutes, user_estimate_source,
                    selected_estimate_type, status
                )
                SELECT user_id, recurrence_rule_id, recurrence_occurrence_at,
                    recurrence_period_start_date + 1, title,
                    start_at, due_at, current_schedule_date, all_day,
                    user_estimated_duration_minutes, user_estimate_source,
                    selected_estimate_type, status
                FROM todos WHERE id = ?
                """, first.getId()), "23505", "uq_todos_recurrence_occurrence");
    }

    @ParameterizedTest
    @MethodSource("invalidUpdates")
    void rejectsRepresentativeCheckViolations(String assignment, String constraint) {
        Todo todo = persistTodo(persistUser("제약사용자"), null);
        // 입력은 아래 고정 테스트 사례이며 사용자 입력을 연결하지 않음
        assertViolation(() -> jdbc.update(
                "UPDATE todos SET " + assignment + " WHERE id = ?", todo.getId()
        ), "23514", constraint);
    }

    static Stream<Arguments> invalidUpdates() {
        return Stream.of(
                Arguments.of("user_estimated_duration_minutes = 0", "ck_todos_user_estimate_positive"),
                Arguments.of("selected_estimate_type = 'AI'", "ck_todos_ai_selection"),
                Arguments.of("recurrence_occurrence_at = start_at", "ck_todos_recurrence_pair"),
                Arguments.of("due_at = start_at", "ck_todos_schedule_order"),
                Arguments.of("title = '   '", "ck_todos_title_not_blank"),
                Arguments.of("all_day = true, due_at = due_at - INTERVAL '1 hour'",
                        "ck_todos_all_day_boundaries")
        );
    }


    @Test
    void savesAndQueriesSessions() {
        User owner = persistUser("세션사용자");
        User other = persistUser("다른세션사용자");
        Todo todo = persistTodo(owner, null);

        Session first = Session.start(todo, START);
        first.finish(START.plusSeconds(1800), SessionEndReason.PAUSED);
        sessionRepository.saveAndFlush(first);

        Session second = sessionRepository.saveAndFlush(
                Session.start(todo, START.plusSeconds(3600))
        );
        entityManager.clear();

        var sessions = sessionRepository
                .findByTodo_IdAndTodo_User_IdOrderByStartedAtAscIdAsc(
                        todo.getId(), owner.getId());
        assertThat(sessions).extracting(Session::getId)
                .containsExactly(first.getId(), second.getId());
        assertThat(sessions.get(0).getDurationSeconds()).isEqualTo(1800);
        assertThat(sessions.get(0).getEndReason()).isEqualTo(SessionEndReason.PAUSED);

        var running = sessionRepository
                .findByTodo_IdAndTodo_User_IdAndEndedAtIsNull(
                        todo.getId(), owner.getId());
        assertThat(running).isPresent();
        assertThat(running.orElseThrow().getId()).isEqualTo(second.getId());
        assertThat(running.orElseThrow().getDurationSeconds()).isNull();

        assertThat(sessionRepository.sumDurationSecondsByTodoIdAndUserId(
                todo.getId(), owner.getId())).isEqualTo(1800L);
        assertThat(sessionRepository
                .findByTodo_IdAndTodo_User_IdAndEndedAtIsNull(
                        todo.getId(), other.getId())).isEmpty();
    }

    @Test
    void rejectsDuplicateOpenSessions() {
        User owner = persistUser("열린세션검사");
        Todo todo = persistTodo(owner, null);
        sessionRepository.saveAndFlush(Session.start(todo, START));

        assertViolation(() -> jdbc.update("""
                INSERT INTO sessions (todo_id, started_at)
                VALUES (?, CURRENT_TIMESTAMP)
                """, todo.getId()), "23505", "uq_sessions_open_todo");
    }

    @Test
    void rejectsIncompleteSessionClosure() {
        User owner = persistUser("세션종료검사");
        Todo todo = persistTodo(owner, null);
        Session session = sessionRepository.saveAndFlush(Session.start(todo, START));

        assertViolation(() -> jdbc.update("""
                UPDATE sessions
                SET ended_at = started_at, end_reason = 'PAUSED'
                WHERE id = ?
                """, session.getId()), "23514", "ck_sessions_end_fields");
    }

    @Test
    void savesAndQueriesDailySnapshots() {
        User owner = persistUser("스냅샷사용자");
        User other = persistUser("다른스냅샷사용자");
        Todo todo = persistTodo(owner, null);

        // 10월 22일: 최초 배정일이므로 이전 날짜에서 이월된 Todo가 아님
        TodoDailySnapshot first = snapshotRepository.saveAndFlush(
                TodoDailySnapshot.capture(
                        todo, SCHEDULE_DATE, false, false, DUE.minusSeconds(1)
                )
        );

        // 이후의 현재 Todo 데이터 변경과 무관하게 전날 Snapshot은 보존되어야 함.
        // 이 UPDATE는 테스트 픽스처이며 실제 자동 이월 기능 구현이 아님.
        Instant completedAt = DUE.plusSeconds(1800);
        jdbc.update("""
                UPDATE todos
                SET current_schedule_date = ?, status = 'COMPLETED', completed_at = ?
                WHERE id = ?
                """, SCHEDULE_DATE.plusDays(1), Timestamp.from(completedAt), todo.getId());
        entityManager.refresh(todo);

        // 10월 23일: 전날에서 이월되어 들어온 Todo
        TodoDailySnapshot second = snapshotRepository.saveAndFlush(
                TodoDailySnapshot.capture(
                        todo, SCHEDULE_DATE.plusDays(1), true, true,
                        DUE.plusSeconds(86400 - 1)
                )
        );
        entityManager.clear();

        var previousDay = snapshotRepository
                .findByTodo_IdAndUser_IdAndScheduleDate(
                        todo.getId(), owner.getId(), SCHEDULE_DATE).orElseThrow();
        assertThat(previousDay.getStatusAtDayEnd()).isEqualTo(TodoStatus.PENDING);
        assertThat(previousDay.isWasRolledOver()).isFalse();
        assertThat(previousDay.isWasOverdue()).isFalse();
        assertThat(previousDay.getCompletedAtSnapshot()).isNull();
        assertThat(previousDay.getStartAtSnapshot()).isEqualTo(START);
        assertThat(previousDay.getDueAtSnapshot()).isEqualTo(DUE);
        assertThat(previousDay.getSnapshotAt()).isEqualTo(DUE.minusSeconds(1));
        assertThat(previousDay.getCreatedAt()).isNotNull();

        var nextDay = snapshotRepository
                .findByTodo_IdAndUser_IdAndScheduleDate(
                        todo.getId(), owner.getId(), SCHEDULE_DATE.plusDays(1)).orElseThrow();
        assertThat(nextDay.getStatusAtDayEnd()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(nextDay.isWasRolledOver()).isTrue();
        assertThat(nextDay.isWasOverdue()).isTrue();
        assertThat(nextDay.getCompletedAtSnapshot()).isEqualTo(completedAt);
        assertThat(nextDay.getStartAtSnapshot()).isEqualTo(START);
        assertThat(nextDay.getDueAtSnapshot()).isEqualTo(DUE);

        assertThat(snapshotRepository
                .findByTodo_IdAndUser_IdAndScheduleDate(
                        todo.getId(), other.getId(), SCHEDULE_DATE)).isEmpty();
        assertThat(snapshotRepository.existsByTodo_IdAndScheduleDate(
                todo.getId(), SCHEDULE_DATE)).isTrue();
        assertThat(snapshotRepository
                .findByUser_IdAndScheduleDateOrderByIdAsc(owner.getId(), SCHEDULE_DATE))
                .extracting(TodoDailySnapshot::getId)
                .containsExactly(first.getId());
        assertThat(snapshotRepository
                .findByUser_IdAndScheduleDateBetweenOrderByScheduleDateAscIdAsc(
                        owner.getId(), SCHEDULE_DATE, SCHEDULE_DATE.plusDays(1)))
                .extracting(TodoDailySnapshot::getId)
                .containsExactly(first.getId(), second.getId());
        assertThat(snapshotRepository.countByUser_IdAndScheduleDateAndStatusAtDayEnd(
                owner.getId(), SCHEDULE_DATE, TodoStatus.PENDING)).isEqualTo(1L);
        assertThat(snapshotRepository.countByUser_IdAndScheduleDateAndStatusAtDayEnd(
                owner.getId(), SCHEDULE_DATE.plusDays(1), TodoStatus.COMPLETED)).isEqualTo(1L);
    }

    @Test
    void rejectsDuplicateDailySnapshotForTheSameTodoAndDate() {
        User owner = persistUser("스냅샷중복검사");
        Todo todo = persistTodo(owner, null);
        snapshotRepository.saveAndFlush(TodoDailySnapshot.capture(
                todo, SCHEDULE_DATE, false, false, DUE.minusSeconds(1)));

        assertViolation(() -> jdbc.update("""
                INSERT INTO todo_daily_snapshots (
                    todo_id, user_id, schedule_date, status_at_day_end,
                    start_at_snapshot, due_at_snapshot,
                    was_rolled_over, was_overdue, snapshot_at
                )
                SELECT todo_id, user_id, schedule_date, status_at_day_end,
                    start_at_snapshot, due_at_snapshot,
                    was_rolled_over, was_overdue, snapshot_at
                FROM todo_daily_snapshots
                WHERE todo_id = ? AND schedule_date = ?
                """, todo.getId(), SCHEDULE_DATE),
                "23505", "uq_todo_daily_snapshots_todo_date");
    }

    @Test
    void rejectsUnknownDailySnapshotStatus() {
        User owner = persistUser("스냅샷상태검사");
        Todo todo = persistTodo(owner, null);
        TodoDailySnapshot snapshot = snapshotRepository.saveAndFlush(
                TodoDailySnapshot.capture(
                        todo, SCHEDULE_DATE, false, false, DUE.minusSeconds(1)));

        assertViolation(() -> jdbc.update("""
                UPDATE todo_daily_snapshots
                SET status_at_day_end = 'UNKNOWN'
                WHERE id = ?
                """, snapshot.getId()),
                "23514", "ck_todo_daily_snapshots_status");
    }

    @Test
    void verifiesAllColumnTypesNullabilityKeysAndImportantIndexDefinitions() {
        // '?'는 nullable이며 나머지는 NOT NULL. Hibernate가 검사하지 않는 항목도 대조
        Map<String, String> expected = Map.ofEntries(
                Map.entry("timer_switch_requests", "id:bigint,request_id:uuid,user_id:bigint,source_todo_id:bigint,target_todo_id:bigint,source_session_id:bigint,started_session_id:bigint?,state:varchar(20),requested_at:timestamptz,ready_at:timestamptz,expires_at:timestamptz,created_at:timestamptz,updated_at:timestamptz"),
                Map.entry("user_daily_settlement_states", "user_id:bigint,tracking_started_on:date,next_settlement_date:date,created_at:timestamptz,updated_at:timestamptz"),
                Map.entry("users", "id:bigint,nickname:varchar(8),bio:varchar(255)?,onboarding_completed_at:timestamptz?,created_at:timestamptz,updated_at:timestamptz"),
                Map.entry("auth_accounts", "id:bigint,user_id:bigint,provider:varchar(20),email:varchar(320)?,password_hash:varchar(255)?,provider_user_id:varchar(255)?,last_login_at:timestamptz?,created_at:timestamptz,updated_at:timestamptz"),
                Map.entry("user_settings", "user_id:bigint,task_switch_restriction_enabled:boolean,task_switch_wait_minutes:integer,repeat_recommendation_enabled:boolean,created_at:timestamptz,updated_at:timestamptz"),
                Map.entry("todos", "id:bigint,user_id:bigint,recurrence_rule_id:bigint?,recurrence_occurrence_at:timestamptz?,recurrence_period_start_date:date?,title:varchar(200),source_task_name:varchar(200)?,memo:text?,start_at:timestamptz,due_at:timestamptz,current_schedule_date:date,all_day:boolean,user_estimated_duration_minutes:integer,user_estimate_source:varchar(30),ai_estimated_duration_minutes:integer?,selected_estimate_type:varchar(20),status:varchar(30),completed_at:timestamptz?,created_at:timestamptz,updated_at:timestamptz,deleted_at:timestamptz?"),
                Map.entry("sessions", "id:bigint,todo_id:bigint,started_at:timestamptz,ended_at:timestamptz?,duration_seconds:integer?,end_reason:varchar(20)?,resume_counted:boolean?,created_at:timestamptz,updated_at:timestamptz"),
                Map.entry("todo_daily_snapshots", "id:bigint,todo_id:bigint,user_id:bigint,schedule_date:date,status_at_day_end:varchar(30),start_at_snapshot:timestamptz,due_at_snapshot:timestamptz,completed_at_snapshot:timestamptz?,was_rolled_over:boolean,was_overdue:boolean,snapshot_at:timestamptz,created_at:timestamptz"),
                Map.entry("time_estimation_profiles", "id:bigint,user_id:bigint,duration_correction_factor:float8,sample_count:integer,log_ratio_stddev:float8?,underestimation_rate:float8,profile_reliability_score:float8,created_at:timestamptz,updated_at:timestamptz"),
                Map.entry("todo_embeddings", "todo_id:bigint,embedding:vector(1024),embedding_model:varchar(100),source_text_hash:char(64),created_at:timestamptz,updated_at:timestamptz"),
                Map.entry("time_prediction_experiments", "id:bigint,prediction_run_id:uuid,todo_id:bigint,user_id:bigint,strategy_type:varchar(40),predicted_minutes:integer,actual_minutes:integer?,absolute_error_minutes:integer?,absolute_percentage_error:float8?,baseline_prediction_minutes:integer?,duration_correction_factor:float8?,simple_factor:float8?,profile_reliability_score:float8?,sample_count:integer?,log_ratio_stddev:float8?,underestimation_rate:float8?,context_adjustment_factor:float8?,similar_todo_ids:jsonb?,reason_codes:jsonb?,model_name:varchar(100)?,prompt_version:varchar(50)?,embedding_model:varchar(100)?,created_at:timestamptz,evaluated_at:timestamptz?"),
                Map.entry("repeat_patterns", "id:bigint,user_id:bigint,task_name:varchar(200),frequency:varchar(20),day_of_week:varchar(10)?,scheduled_time:time?,occurrence_count:integer,status:varchar(20),last_occurrence_date:date,last_suggested_at:timestamptz?,created_at:timestamptz,updated_at:timestamptz,all_day:boolean,detection_after_todo_id:bigint"),
                Map.entry("recurrence_rules", "id:bigint,user_id:bigint,source_pattern_id:bigint,task_name:varchar(200),memo:text?,frequency:varchar(20),day_of_week:varchar(10)?,scheduled_time:time?,default_deadline_offset_minutes:integer,default_user_estimated_duration_minutes:integer,enabled:boolean,created_at:timestamptz,updated_at:timestamptz,deleted_at:timestamptz?,all_day:boolean,generation_not_before:timestamptz"),
                Map.entry("character_items", "id:bigint,name:varchar(100),grade:varchar(10),category:varchar(20),asset_key:varchar(500),active:boolean,created_at:timestamptz,updated_at:timestamptz"),
                Map.entry("user_character_items", "id:bigint,user_id:bigint,character_item_id:bigint,acquired_at:timestamptz"),
                Map.entry("user_equipped_items", "id:bigint,user_id:bigint,character_item_id:bigint,category:varchar(20),equipped_at:timestamptz,updated_at:timestamptz"),
                Map.entry("reward_settlements", "id:bigint,user_id:bigint,reward_date:date,assigned_todo_count:integer,completed_todo_count:integer,completion_rate:numeric(5,2),settled_at:timestamptz"),
                Map.entry("reward_grants", "id:bigint,user_id:bigint,character_item_id:bigint,reward_date:date,reward_grade:varchar(10),completion_rate:numeric(5,2),granted_at:timestamptz")
        );
        expected.forEach((table, columns) -> {
            var actual = jdbc.queryForList("""
                    SELECT a.attname || ':' || format_type(a.atttypid, a.atttypmod)
                        || CASE WHEN a.attnotnull THEN '' ELSE '?' END
                    FROM pg_attribute a JOIN pg_class t ON t.oid = a.attrelid
                    JOIN pg_namespace n ON n.oid = t.relnamespace
                    WHERE n.nspname = 'public' AND t.relname = ?
                        AND a.attnum > 0 AND NOT a.attisdropped
                    """, String.class, table).stream()
                    .map(s -> s.replace("timestamp with time zone", "timestamptz")
                            .replace("time without time zone", "time")
                            .replace("character varying", "varchar")
                            .replace("character(", "char(").replace("double precision", "float8"))
                    .toList();
            assertThat(actual).as(table).containsExactlyInAnyOrder(columns.split(",(?=[a-z_]+:)"));
        });
        assertThat(entityManager.getMetamodel().getEntities()).hasSize(21);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND is_identity='YES'
                """, Long.class)).isEqualTo(18);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace
                WHERE n.nspname='public' AND c.contype='f' AND c.confdeltype='a'
                """, Long.class)).isEqualTo(23);
        assertThat(jdbc.queryForList("""
                SELECT child.relname || '.' || ca.attname || '->' || parent.relname || '.' || pa.attname
                FROM pg_constraint c JOIN pg_class child ON child.oid=c.conrelid
                JOIN pg_class parent ON parent.oid=c.confrelid
                JOIN pg_namespace n ON n.oid=child.relnamespace
                JOIN pg_attribute ca ON ca.attrelid=child.oid AND ca.attnum=c.conkey[1]
                JOIN pg_attribute pa ON pa.attrelid=parent.oid AND pa.attnum=c.confkey[1]
                WHERE c.contype='f' AND n.nspname='public'
                """, String.class)).containsExactlyInAnyOrder(
                "auth_accounts.user_id->users.id", "user_settings.user_id->users.id", "todos.user_id->users.id",
                "sessions.todo_id->todos.id", "todo_daily_snapshots.todo_id->todos.id",
                "todo_daily_snapshots.user_id->users.id", "time_estimation_profiles.user_id->users.id",
                "todo_embeddings.todo_id->todos.id", "time_prediction_experiments.todo_id->todos.id",
                "time_prediction_experiments.user_id->users.id", "repeat_patterns.user_id->users.id",
                "recurrence_rules.user_id->users.id", "recurrence_rules.source_pattern_id->repeat_patterns.id",
                "todos.recurrence_rule_id->recurrence_rules.id",
                "user_character_items.user_id->users.id", "user_character_items.character_item_id->character_items.id",
                "user_equipped_items.user_id->users.id", "user_equipped_items.user_id->user_character_items.user_id",
                "user_equipped_items.character_item_id->character_items.id", "reward_settlements.user_id->users.id",
                "reward_grants.user_id->users.id", "reward_grants.character_item_id->character_items.id",
                "reward_grants.user_id->reward_settlements.user_id",
                "ai_drafts.user_id->users.id", "ai_drafts.todo_id->todos.id", "ai_drafts.parent_id->ai_drafts.id",
                "ai_operations.draft_id->ai_drafts.id", "ai_creation_requests.user_id->users.id", "ai_creation_requests.draft_id->ai_drafts.id",
                "timer_switch_requests.user_id->users.id", "timer_switch_requests.source_todo_id->todos.id",
                "timer_switch_requests.target_todo_id->todos.id", "timer_switch_requests.source_session_id->sessions.id",
                "timer_switch_requests.started_session_id->sessions.id", "user_daily_settlement_states.user_id->users.id");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public' AND c.contype='f' AND c.confdeltype='c'",Long.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname='fk_recurrence_rules_pattern_owner'
                """, String.class)).isEqualTo(
                "FOREIGN KEY (source_pattern_id, user_id) REFERENCES repeat_patterns(id, user_id)");
        assertThat(jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname='fk_todos_recurrence_rule_owner'
                """, String.class)).isEqualTo(
                "FOREIGN KEY (recurrence_rule_id, user_id) REFERENCES recurrence_rules(id, user_id)");
        assertThat(jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname=?",
                String.class, "fk_user_equipped_items_owned")).isEqualTo(
                "FOREIGN KEY (user_id, character_item_id) REFERENCES user_character_items(user_id, character_item_id)");
        assertThat(jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname=?",
                String.class, "fk_user_equipped_items_item_category")).isEqualTo(
                "FOREIGN KEY (character_item_id, category) REFERENCES character_items(id, category)");
        assertThat(jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname=?",
                String.class, "fk_reward_grants_item_grade")).isEqualTo(
                "FOREIGN KEY (character_item_id, reward_grade) REFERENCES character_items(id, grade)");
        assertThat(jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname=?",
                String.class, "fk_reward_grants_settlement")).isEqualTo(
                "FOREIGN KEY (user_id, reward_date) REFERENCES reward_settlements(user_id, reward_date)");
        assertThat(jdbc.queryForList("""
                SELECT conname FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace
                WHERE n.nspname='public' AND c.contype='c'
                """, String.class)).hasSize(71).contains("ck_repeat_patterns_detection_boundary", "ck_repeat_patterns_schedule", "ck_recurrence_rules_schedule", "ck_daily_settlement_date", "ck_timer_switch_state", "ck_timer_switch_distinct", "ck_timer_switch_time", "ck_timer_switch_started", "ck_auth_accounts_provider", "ck_auth_accounts_fields",
                        "ck_repeat_patterns_task_name", "ck_repeat_patterns_frequency",
                        "ck_repeat_patterns_day_of_week", "ck_repeat_patterns_occurrence_count",
                        "ck_repeat_patterns_status", "ck_recurrence_rules_task_name",
                        "ck_recurrence_rules_frequency", "ck_recurrence_rules_day_of_week",
                        "ck_recurrence_rules_deadline_offset", "ck_recurrence_rules_user_estimate",
                        "ck_todos_recurrence_period_pair",
                        "ck_character_items_name", "ck_character_items_asset_key", "ck_character_items_grade",
                        "ck_character_items_category", "ck_user_equipped_items_category",
                        "ck_reward_settlements_todo_counts", "ck_reward_settlements_completion_rate",
                        "ck_reward_grants_grade", "ck_reward_grants_completion_rate");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint
                WHERE conrelid='todos'::regclass AND contype='f'
                """, Long.class)).isEqualTo(2);
        for (String table : expected.keySet()) {
            String pk = (table.equals("user_settings") || table.equals("user_daily_settlement_states")) ? "user_id"
                    : table.equals("todo_embeddings") ? "todo_id" : "id";
            assertThat(jdbc.queryForObject("""
                    SELECT pg_get_constraintdef(oid) FROM pg_constraint
                    WHERE conrelid=CAST(? AS regclass) AND contype='p'
                    """, String.class, table)).isEqualTo("PRIMARY KEY (" + pk + ")");
        }
        Map<String, String> indexes = jdbc.query("""
                SELECT indexname, indexdef FROM pg_indexes WHERE schemaname='public'
                """, rs -> {
            Map<String, String> result = new java.util.HashMap<>();
            while (rs.next()) result.put(rs.getString(1), rs.getString(2));
            return result;
        });
        assertThat(indexes.get("uq_character_items_id_category")).contains("UNIQUE", "(id, category)");
        assertThat(indexes.get("uq_character_items_id_grade")).contains("UNIQUE", "(id, grade)");
        assertThat(indexes.get("uq_user_character_items_user_item")).contains("UNIQUE", "(user_id, character_item_id)");
        assertThat(indexes.get("uq_user_equipped_items_user_category")).contains("UNIQUE", "(user_id, category)");
        assertThat(indexes.get("uq_reward_settlements_user_date")).contains("UNIQUE", "(user_id, reward_date)");
        assertThat(indexes.get("uq_reward_grants_user_date_grade")).contains("UNIQUE", "(user_id, reward_date, reward_grade)");
        assertThat(indexes.get("uq_reward_grants_user_item")).contains("UNIQUE", "(user_id, character_item_id)");
        assertThat(indexes.get("ix_character_items_active_grade")).contains("(grade, id)", "WHERE", "active");
        assertThat(indexes.get("uq_todos_recurrence_occurrence"))
                .contains("UNIQUE", "(recurrence_rule_id, recurrence_occurrence_at)",
                        "WHERE (recurrence_rule_id IS NOT NULL)")
                .doesNotContain("deleted_at");
        assertThat(indexes.get("uq_todos_recurrence_period"))
                .contains("UNIQUE", "(recurrence_rule_id, recurrence_period_start_date)",
                        "WHERE (recurrence_rule_id IS NOT NULL)").doesNotContain("deleted_at");
        assertThat(indexes.get("uq_recurrence_rules_source_pattern"))
                .contains("UNIQUE", "(source_pattern_id)").doesNotContain("WHERE");
        assertThat(indexes.get("uq_repeat_patterns_id_user")).contains("UNIQUE", "(id, user_id)");
        assertThat(indexes.get("uq_recurrence_rules_id_user")).contains("UNIQUE", "(id, user_id)");
        assertThat(indexes.get("ix_repeat_patterns_user_status"))
                .contains("(user_id, status, last_suggested_at DESC, id DESC)");
        assertThat(indexes.get("ix_recurrence_rules_user_active"))
                .contains("(user_id, enabled, id)", "WHERE (deleted_at IS NULL)");
        assertThat(indexes.get("ix_todos_user_schedule_active"))
                .contains("(user_id, current_schedule_date, start_at, id)", "WHERE (deleted_at IS NULL)");
        assertThat(indexes.get("ix_todos_user_status_active"))
                .contains("(user_id, status, start_at, id)", "WHERE (deleted_at IS NULL)");
        assertThat(indexes.get("uq_timer_switch_waiting_user")).contains("UNIQUE", "(user_id)", "WAITING");
        assertThat(indexes.get("ix_daily_settlement_pending")).contains("(next_settlement_date, user_id)");
        assertThat(indexes.get("uq_sessions_open_todo")).contains("UNIQUE", "WHERE (ended_at IS NULL)");
        assertThat(indexes.get("ix_sessions_todo_started")).contains("(todo_id, started_at, id)");
        assertThat(indexes.get("ix_todo_daily_snapshots_user_date")).contains("(user_id, schedule_date)");
        assertThat(indexes.get("uq_time_estimation_profiles_user")).contains("UNIQUE", "(user_id)");
        assertThat(indexes.get("ix_time_prediction_experiments_run")).contains("(prediction_run_id)");
        assertThat(indexes.get("ix_time_prediction_experiments_todo_user_created"))
                .contains("(todo_id, user_id, created_at DESC, id DESC)");
        assertThat(indexes.get("ix_time_prediction_experiments_evaluated_strategy"))
                .contains("(user_id, strategy_type)", "WHERE (evaluated_at IS NOT NULL)");
        assertThat(indexes.get("ux_auth_accounts_local_email")).contains("lower", "LOCAL");
        assertThat(indexes.get("ux_auth_accounts_social_identity")).contains("(provider, provider_user_id)");
        assertThat(indexes.get("ux_auth_accounts_user_local")).contains("UNIQUE", "(user_id)", "LOCAL");
        assertThat(indexes.get("ux_auth_accounts_user_social")).contains("UNIQUE", "(user_id)", "GOOGLE");
        assertThat(indexes.values()).noneMatch(s -> s.contains("USING hnsw"));
    }

    @Test
    void savesBasicUserAuthSettingMappingsAndQueriesLocalEmailIgnoringCase() {
        User user = userRepository.saveAndFlush(new User("기본사용자"));
        AuthAccount local = authRepository.saveAndFlush(
                AuthAccount.local(user, "fixture@atch.invalid", UUID.randomUUID().toString()));
        AuthAccount social = authRepository.saveAndFlush(
                AuthAccount.social(user, AuthProvider.GOOGLE, null, "synthetic-google"));
        settingRepository.saveAndFlush(new UserSetting(user));
        entityManager.clear();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getNickname()).isEqualTo("기본사용자");
        assertThat(authRepository.findByProviderAndEmailIgnoreCase(AuthProvider.LOCAL, "FIXTURE@ATCH.INVALID"))
                .get().extracting(AuthAccount::getId).isEqualTo(local.getId());
        assertThat(authRepository.existsByProviderAndEmailIgnoreCase(AuthProvider.LOCAL, "FIXTURE@ATCH.INVALID")).isTrue();
        assertThat(authRepository.existsByProviderAndEmailIgnoreCase(AuthProvider.LOCAL, "absent@atch.invalid")).isFalse();
        assertThat(authRepository.findById(social.getId()).orElseThrow().getProviderUserId()).isEqualTo("synthetic-google");
        var setting = settingRepository.findById(user.getId()).orElseThrow();
        assertThat(setting.getUserId()).isEqualTo(user.getId());
        assertThat(setting.isTaskSwitchRestrictionEnabled()).isTrue();
        assertThat(setting.isRepeatRecommendationEnabled()).isTrue();
        assertThat(setting.getTaskSwitchWaitMinutes()).isEqualTo(10);
    }

    @Test
    void queriesScheduleBoundariesStatusesAndStableOrdering() {
        User owner = persistUser("기간사용자");
        Todo first = persistTodo(owner, null);
        Todo second = persistTodo(owner, null);
        Todo last = persistTodo(owner, null);
        Todo outside = persistTodo(owner, null);
        persistTodo(persistUser("기간다른이"), null);
        jdbc.update("UPDATE todos SET current_schedule_date=?, status='COMPLETED', completed_at=? WHERE id=?",
                SCHEDULE_DATE.plusDays(2), Timestamp.from(DUE), last.getId());
        jdbc.update("UPDATE todos SET current_schedule_date=? WHERE id=?", SCHEDULE_DATE.plusDays(3), outside.getId());
        entityManager.clear();
        assertThat(todoRepository.findAllByUser_IdAndCurrentScheduleDateAndDeletedAtIsNullOrderByStartAtAscIdAsc(
                owner.getId(), SCHEDULE_DATE)).extracting(Todo::getId).containsExactly(first.getId(), second.getId());
        var page = todoRepository.findAllByUser_IdAndCurrentScheduleDateBetweenAndDeletedAtIsNull(
                owner.getId(), SCHEDULE_DATE, SCHEDULE_DATE.plusDays(2),
                PageRequest.of(0, 2, Sort.by("id")));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(Todo::getId).containsExactly(first.getId(), second.getId());
        assertThat(todoRepository.findAllByUser_IdAndStatusAndDeletedAtIsNullOrderByStartAtAscIdAsc(
                owner.getId(), TodoStatus.COMPLETED)).extracting(Todo::getId).containsExactly(last.getId());
    }

    @Test
    void checksSessionLifecycleAndAllOwnedOpenSessionsWithoutUserWideRestriction() {
        User owner = persistUser("구간사용자");
        User other = persistUser("구간다른이");
        Todo first = persistTodo(owner, null);
        Todo second = persistTodo(owner, null);
        Session zero = Session.start(first, START);
        zero.finish(START, SessionEndReason.COMPLETED);
        sessionRepository.saveAndFlush(zero);
        assertThrows(IllegalStateException.class, () -> zero.finish(START, SessionEndReason.PAUSED));
        Session switched = Session.start(first, START.plusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> switched.finish(START, SessionEndReason.PAUSED));
        assertThrows(IllegalArgumentException.class,
                () -> switched.finish(START.plusSeconds((long) Integer.MAX_VALUE + 2), SessionEndReason.PAUSED));
        switched.finish(START.plusSeconds(6), SessionEndReason.SWITCHED);
        sessionRepository.saveAndFlush(switched);
        Session open1 = sessionRepository.saveAndFlush(Session.start(first, START.plusSeconds(7)));
        Session open2 = sessionRepository.saveAndFlush(Session.start(second, START));
        entityManager.clear();
        assertThat(sessionRepository.findByTodo_User_IdAndEndedAtIsNull(owner.getId()))
                .extracting(Session::getId).containsExactlyInAnyOrder(open1.getId(), open2.getId());
        assertThat(sessionRepository.findByTodo_User_IdAndEndedAtIsNull(other.getId())).isEmpty();
        assertThat(sessionRepository.sumDurationSecondsByTodoIdAndUserId(first.getId(), owner.getId())).isEqualTo(5);
        assertThat(sessionRepository.sumDurationSecondsByTodoIdAndUserId(first.getId(), other.getId())).isZero();
        assertThat(sessionRepository.sumDurationSecondsByTodoIdAndUserId(second.getId(), owner.getId())).isZero();
        var found = sessionRepository.findById(zero.getId()).orElseThrow();
        assertThat(found.getDurationSeconds()).isZero();
        assertThat(found.getEndReason()).isEqualTo(SessionEndReason.COMPLETED);
        assertThat(sessionRepository.findById(switched.getId()).orElseThrow().getEndReason())
                .isEqualTo(SessionEndReason.SWITCHED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesSnapshotTimesAndIndependentFlags(boolean rolledOver) {
        Todo todo = persistTodo(persistUser("기록사용자"), null);
        var snapshot = snapshotRepository.saveAndFlush(TodoDailySnapshot.capture(
                todo, SCHEDULE_DATE, rolledOver, !rolledOver, DUE));
        jdbc.update("UPDATE todos SET start_at=start_at+INTERVAL '1 hour', due_at=due_at+INTERVAL '2 hours' WHERE id=?",
                todo.getId());
        entityManager.clear();
        var found = snapshotRepository.findById(snapshot.getId()).orElseThrow();
        assertThat(found.getUser().getId()).isEqualTo(todo.getUser().getId());
        assertThat(found.getStartAtSnapshot()).isEqualTo(START);
        assertThat(found.getDueAtSnapshot()).isEqualTo(DUE);
        assertThat(found.isWasRolledOver()).isEqualTo(rolledOver);
        assertThat(found.isWasOverdue()).isEqualTo(!rolledOver);
        assertThrows(IllegalArgumentException.class, () -> TodoDailySnapshot.capture(
                Todo.builder().user(todo.getUser()).title("미저장").startAt(START).dueAt(DUE)
                        .currentScheduleDate(SCHEDULE_DATE).userEstimatedDurationMinutes(1)
                        .userEstimateSource(UserEstimateSource.USER_INPUT).selectedEstimateType(SelectedEstimateType.USER).build(),
                SCHEDULE_DATE, false, false, DUE));
    }

    @Test
    void savesUpdatesAndQueriesProfileWithZeroSamplesAndNullableStddev() {
        User owner = persistUser("프로필사용자");
        User other = persistUser("프로필다른이");
        var profile = profileRepository.saveAndFlush(TimeEstimationProfile.create(owner, 1.0, 0, null, 0, 1));
        entityManager.clear();
        var found = profileRepository.findByUser_Id(owner.getId()).orElseThrow();
        assertThat(found.getId()).isEqualTo(profile.getId());
        assertThat(found.getSampleCount()).isZero();
        assertThat(found.getLogRatioStddev()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        Instant updated = found.getUpdatedAt();
        found.updateMetrics(1.25, 3, 0.15, 1, 0.5);
        profileRepository.flush();
        entityManager.clear();
        var changed = profileRepository.findByUser_Id(owner.getId()).orElseThrow();
        assertThat(changed.getDurationCorrectionFactor()).isEqualTo(1.25);
        assertThat(changed.getSampleCount()).isEqualTo(3);
        assertThat(changed.getLogRatioStddev()).isEqualTo(0.15);
        assertThat(changed.getUnderestimationRate()).isEqualTo(1);
        assertThat(changed.getProfileReliabilityScore()).isEqualTo(0.5);
        assertThat(changed.getUpdatedAt()).isAfterOrEqualTo(updated);
        assertThat(profileRepository.findByUser_Id(other.getId())).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidProfileMetrics")
    void rejectsInvalidProfileMetricsInJava(double factor, int samples, Double stddev, double rate, double reliability) {
        var profile = TimeEstimationProfile.create(persistUser("수치사용자"), 1, 0, null, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> profile.updateMetrics(factor, samples, stddev, rate, reliability));
        assertThat(profile.getDurationCorrectionFactor()).isEqualTo(1);
        assertThat(profile.getSampleCount()).isZero();
    }

    static Stream<Arguments> invalidProfileMetrics() {
        return Stream.of(
                Arguments.of(0, 0, null, 0, 0),
                Arguments.of(Double.NaN, 0, null, 0, 0),
                Arguments.of(Double.POSITIVE_INFINITY, 0, null, 0, 0),
                Arguments.of(1, -1, null, 0, 0),
                Arguments.of(1, 0, -0.1, 0, 0),
                Arguments.of(1, 0, Double.NaN, 0, 0),
                Arguments.of(1, 0, Double.POSITIVE_INFINITY, 0, 0),
                Arguments.of(1, 0, null, -0.1, 0),
                Arguments.of(1, 0, null, 1.1, 0),
                Arguments.of(1, 0, null, Double.NaN, 0),
                Arguments.of(1, 0, null, 0, Double.POSITIVE_INFINITY),
                Arguments.of(1, 0, null, 0, -0.1),
                Arguments.of(1, 0, null, 0, 1.1));
    }

    @Test
    void savesReplacesAndQueriesEmbeddingWithSharedPkAndDefensiveCopies() {
        User owner = persistUser("벡터사용자");
        User other = persistUser("벡터다른이");
        Todo todo = persistTodo(owner, null);
        float[] vector = vector();
        var embedding = TodoEmbedding.create(todo, vector, "fixture-vector", "aB".repeat(32));
        vector[0] = 99;
        float[] returned = embedding.getEmbedding();
        returned[1] = 99;
        embeddingRepository.saveAndFlush(embedding);
        entityManager.clear();
        var found = embeddingRepository.findByTodo_IdAndTodo_User_Id(todo.getId(), owner.getId()).orElseThrow();
        assertThat(found.getTodoId()).isEqualTo(todo.getId());
        assertThat(found.getTodo().getId()).isEqualTo(todo.getId());
        assertThat(found.getEmbedding()).containsExactly(vector());
        assertThat(found.getEmbeddingModel()).isEqualTo("fixture-vector");
        assertThat(found.getSourceTextHash()).isEqualTo("aB".repeat(32));
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT vector_dims(embedding) FROM todo_embeddings WHERE todo_id=?",
                Integer.class, todo.getId())).isEqualTo(1024);
        assertThat(embeddingRepository.findByTodo_IdAndTodo_User_Id(todo.getId(), other.getId())).isEmpty();
        assertThat(embeddingRepository.findByTodo_IdAndTodo_User_Id(-1L, owner.getId())).isEmpty();
        float[] replacement = vector();
        replacement[0] = -0.25f;
        found.replaceEmbedding(replacement, "replacement-vector", "F".repeat(64));
        replacement[0] = 99;
        embeddingRepository.flush();
        entityManager.clear();
        var changed = embeddingRepository.findById(todo.getId()).orElseThrow();
        assertThat(changed.getEmbedding()[0]).isEqualTo(-0.25f);
        assertThat(changed.getSourceTextHash()).isEqualTo("F".repeat(64));
        assertThat(changed.getEmbeddingModel()).isEqualTo("replacement-vector");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dimension", "nan", "infinity", "blank-model", "long-model", "short-hash", "invalid-hash"})
    void rejectsInvalidEmbeddingInputsInJava(String invalid) {
        float[] vector = vector();
        String model = "fixture";
        String hash = "a".repeat(64);
        switch (invalid) {
            case "dimension" -> vector = new float[1023];
            case "nan" -> vector[0] = Float.NaN;
            case "infinity" -> vector[0] = Float.POSITIVE_INFINITY;
            case "blank-model" -> model = "\t";
            case "long-model" -> model = "m".repeat(101);
            case "short-hash" -> hash = "a".repeat(63);
            case "invalid-hash" -> hash = "g".repeat(64);
        }
        float[] input = vector;
        String inputModel = model;
        String inputHash = hash;
        assertThrows(IllegalArgumentException.class, () -> TodoEmbedding.create(
                persistTodo(persistUser("입력사용자"), null), input, inputModel, inputHash));
    }

    @Test
    void savesExperimentUuidJsonSnapshotsEvaluationAndAllRepositoryQueries() {
        User owner = persistUser("실험사용자");
        User other = persistUser("실험다른이");
        Todo todo = persistTodo(owner, null);
        Todo otherTodo = persistTodo(other, null);
        UUID run = UUID.randomUUID();
        List<Long> ids = List.of(12L, 3_000_000_000L);
        List<PredictionReasonCode> reasons = Arrays.asList(PredictionReasonCode.values());
        var saved = new java.util.ArrayList<TimePredictionExperiment>();
        for (PredictionStrategy strategy : PredictionStrategy.values()) {
            var experiment = TimePredictionExperiment.create(run, todo, strategy, 45);
            if (strategy == PredictionStrategy.HYBRID) {
                experiment.recordProfileSnapshot(40, 1.2, 1.1, 0.7, 9, 0.15, 0.8);
                experiment.recordContextSnapshot(1.125, ids, reasons, "fixture-llm", "v1", "fixture-vector");
                experiment.recordEvaluation(0, 45, null, DUE);
            } else if (strategy == PredictionStrategy.SIMPLE_FACTOR) {
                experiment.recordContextSnapshot(null, List.of(), List.of(), null, null, null);
            }
            saved.add(experimentRepository.saveAndFlush(experiment));
        }
        experimentRepository.saveAndFlush(TimePredictionExperiment.create(run, otherTodo, PredictionStrategy.HYBRID, 20));
        var older = experimentRepository.saveAndFlush(TimePredictionExperiment.create(
                UUID.randomUUID(), todo, PredictionStrategy.HYBRID, 30));
        // 고정된 시각으로 created_at 동률과 id 역순까지 검증
        jdbc.update("UPDATE time_prediction_experiments SET created_at=? WHERE todo_id=?",
                Timestamp.from(START), todo.getId());
        jdbc.update("UPDATE time_prediction_experiments SET created_at=? WHERE id=?",
                Timestamp.from(START.minusSeconds(1)), older.getId());
        entityManager.clear();
        var runResults = experimentRepository.findByPredictionRunIdAndUser_IdOrderByIdAsc(run, owner.getId());
        assertThat(runResults).extracting(TimePredictionExperiment::getId)
                .containsExactlyElementsOf(saved.stream().map(TimePredictionExperiment::getId).toList());
        assertThat(runResults).extracting(TimePredictionExperiment::getStrategyType)
                .containsExactly(PredictionStrategy.values());
        var hybrid = runResults.get(3);
        assertThat(hybrid.getPredictionRunId()).isEqualTo(run);
        assertThat(hybrid.getUser().getId()).isEqualTo(owner.getId());
        assertThat(hybrid.getTodo().getId()).isEqualTo(todo.getId());
        assertThat(hybrid.getPredictedMinutes()).isEqualTo(45);
        assertThat(hybrid.getBaselinePredictionMinutes()).isEqualTo(40);
        assertThat(hybrid.getDurationCorrectionFactor()).isEqualTo(1.2);
        assertThat(hybrid.getSimpleFactor()).isEqualTo(1.1);
        assertThat(hybrid.getProfileReliabilityScore()).isEqualTo(0.7);
        assertThat(hybrid.getSampleCount()).isEqualTo(9);
        assertThat(hybrid.getLogRatioStddev()).isEqualTo(0.15);
        assertThat(hybrid.getUnderestimationRate()).isEqualTo(0.8);
        assertThat(hybrid.getContextAdjustmentFactor()).isEqualTo(1.125);
        assertThat(hybrid.getSimilarTodoIds()).containsExactlyElementsOf(ids);
        assertThat(hybrid.getReasonCodes()).containsExactlyElementsOf(reasons);
        assertThat(hybrid.getModelName()).isEqualTo("fixture-llm");
        assertThat(hybrid.getPromptVersion()).isEqualTo("v1");
        assertThat(hybrid.getEmbeddingModel()).isEqualTo("fixture-vector");
        assertThat(hybrid.getActualMinutes()).isZero();
        assertThat(hybrid.getAbsoluteErrorMinutes()).isEqualTo(45);
        assertThat(hybrid.getAbsolutePercentageError()).isNull();
        assertThat(hybrid.getEvaluatedAt()).isEqualTo(DUE);
        assertThat(runResults.get(0).getSimilarTodoIds()).isNull();
        assertThat(runResults.get(0).getReasonCodes()).isNull();
        assertThat(runResults.get(0).getActualMinutes()).isNull();
        assertThat(runResults.get(1).getSimilarTodoIds()).isEmpty();
        assertThat(runResults.get(1).getReasonCodes()).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT jsonb_typeof(similar_todo_ids->1) FROM time_prediction_experiments WHERE id=?
                """, String.class, hybrid.getId())).isEqualTo("number");
        assertThat(jdbc.queryForObject("""
                SELECT reason_codes->>0 FROM time_prediction_experiments WHERE id=?
                """, String.class, hybrid.getId())).isEqualTo(PredictionReasonCode.INSUFFICIENT_HISTORY.name());
        assertThat(experimentRepository.findByIdAndUser_Id(hybrid.getId(), other.getId())).isEmpty();
        assertThat(experimentRepository.findByIdAndUser_Id(hybrid.getId(), owner.getId())).isPresent();
        assertThat(experimentRepository.findByPredictionRunIdAndUser_IdOrderByIdAsc(UUID.randomUUID(), owner.getId())).isEmpty();
        assertThat(experimentRepository.findByTodo_IdAndUser_IdOrderByCreatedAtDescIdDesc(todo.getId(), other.getId())).isEmpty();
        assertThat(experimentRepository.findByTodo_IdAndUser_IdOrderByCreatedAtDescIdDesc(todo.getId(), owner.getId()))
                .extracting(TimePredictionExperiment::getId)
                .containsExactly(saved.get(3).getId(), saved.get(2).getId(), saved.get(1).getId(), saved.get(0).getId(), older.getId());
        assertThat(experimentRepository.findByUser_IdAndStrategyTypeAndEvaluatedAtIsNotNull(owner.getId(), PredictionStrategy.HYBRID))
                .extracting(TimePredictionExperiment::getId).containsExactly(hybrid.getId());
        assertThat(experimentRepository.findByUser_IdAndStrategyTypeAndEvaluatedAtIsNotNull(owner.getId(), PredictionStrategy.USER_ESTIMATE)).isEmpty();
        assertThrows(IllegalArgumentException.class, () -> TimePredictionExperiment.create(run,
                transientTodo(owner), PredictionStrategy.USER_ESTIMATE, 1));
        assertThrows(IllegalArgumentException.class, () -> TimePredictionExperiment.create(run, todo, PredictionStrategy.HYBRID, 0));
        assertThrows(IllegalArgumentException.class, () -> hybrid.recordEvaluation(-1, 0, null, DUE));
        assertThrows(IllegalArgumentException.class, () -> hybrid.recordEvaluation(1, -1, null, DUE));
        for (double invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> hybrid.recordEvaluation(1, 0, invalid, DUE));
        }
        hybrid.recordEvaluation(50, 123, 7.5, DUE);
        experimentRepository.flush();
        entityManager.clear();
        var evaluated = experimentRepository.findById(hybrid.getId()).orElseThrow();
        assertThat(evaluated.getActualMinutes()).isEqualTo(50);
        assertThat(evaluated.getAbsoluteErrorMinutes()).isEqualTo(123); // 재계산하지 않고 전달값 저장
        assertThat(evaluated.getAbsolutePercentageError()).isEqualTo(7.5);
    }

    @Test
    void documentsCurrentlyUnenforcedCrossOwnerDuplicateEvaluationAndJsonElementPolicies() {
        User owner = persistUser("정책사용자");
        User other = persistUser("정책다른이");
        Todo todo = persistTodo(owner, null);
        UUID run = UUID.randomUUID();
        var first = experimentRepository.saveAndFlush(TimePredictionExperiment.create(run, todo, PredictionStrategy.HYBRID, 10));
        experimentRepository.saveAndFlush(TimePredictionExperiment.create(run, todo, PredictionStrategy.HYBRID, 10));
        assertThat(experimentRepository.findByPredictionRunIdAndUser_IdOrderByIdAsc(run, owner.getId())).hasSize(2);
        jdbc.update("""
                UPDATE time_prediction_experiments SET user_id=?, evaluated_at=?,
                    baseline_prediction_minutes=-1, duration_correction_factor=-1,
                    simple_factor=-1, profile_reliability_score=2, log_ratio_stddev=-1,
                    underestimation_rate=2, context_adjustment_factor=-1
                WHERE id=?
                """, other.getId(), Timestamp.from(DUE), first.getId());
        entityManager.clear();
        var inconsistent = experimentRepository.findByIdAndUser_Id(first.getId(), other.getId()).orElseThrow();
        assertThat(inconsistent.getTodo().getUser().getId()).isEqualTo(owner.getId());
        assertThat(inconsistent.getActualMinutes()).isNull();
        assertThat(inconsistent.getEvaluatedAt()).isNotNull();
        // DB는 배열 원소의 타입·허용 Enum을 검사하지 않으며 JPA 역직렬화 성공으로 간주하지 않음
        jdbc.update("""
                UPDATE time_prediction_experiments
                SET similar_todo_ids='["invalid", null]'::jsonb, reason_codes='[123, "UNKNOWN"]'::jsonb
                WHERE id=?
                """, first.getId());
        assertThat(jdbc.queryForObject("""
                SELECT jsonb_typeof(reason_codes->0) FROM time_prediction_experiments WHERE id=?
                """, String.class, first.getId())).isEqualTo("number");
        var snapshot = snapshotRepository.saveAndFlush(TodoDailySnapshot.capture(todo, SCHEDULE_DATE, false, false, DUE));
        jdbc.update("UPDATE todo_daily_snapshots SET user_id=? WHERE id=?", other.getId(), snapshot.getId());
        entityManager.clear();
        assertThat(snapshotRepository.findByTodo_IdAndUser_IdAndScheduleDate(todo.getId(), other.getId(), SCHEDULE_DATE)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity"})
    void documentsSqlLowerBoundsAllowingNonfiniteValuesRejectedByJava(String special) {
        var profile = profileRepository.saveAndFlush(TimeEstimationProfile.create(
                persistUser("특수수치"), 1, 0, null, 0, 0));
        jdbc.update("UPDATE time_estimation_profiles SET duration_correction_factor=CAST(? AS float8), log_ratio_stddev=CAST(? AS float8) WHERE id=?",
                special, special, profile.getId());
        assertThat(jdbc.queryForObject("SELECT duration_correction_factor::text FROM time_estimation_profiles WHERE id=?",
                String.class, profile.getId())).isEqualTo(special);
        Todo todo = persistTodo(profile.getUser(), null);
        var experiment = experimentRepository.saveAndFlush(TimePredictionExperiment.create(
                UUID.randomUUID(), todo, PredictionStrategy.USER_ESTIMATE, 1));
        jdbc.update("UPDATE time_prediction_experiments SET absolute_percentage_error=CAST(? AS float8) WHERE id=?",
                special, experiment.getId());
        assertThat(jdbc.queryForObject("SELECT absolute_percentage_error::text FROM time_prediction_experiments WHERE id=?",
                String.class, experiment.getId())).isEqualTo(special);
    }

    @ParameterizedTest
    @MethodSource("invalidDatabaseUpdates")
    void rejectsDatabaseConstraintsIndependentlyOfJava(String table, String assignment, String state, String detail) {
        User owner = persistUser("무결성검사");
        Todo todo = persistTodo(owner, null);
        long id = switch (table) {
            case "users" -> owner.getId();
            case "auth_accounts" -> authRepository.saveAndFlush(AuthAccount.local(
                    owner, "constraint@atch.invalid", UUID.randomUUID().toString())).getId();
            case "user_settings" -> settingRepository.saveAndFlush(new UserSetting(owner)).getUserId();
            case "todos" -> todo.getId();
            case "sessions" -> {
                Session session = Session.start(todo, START);
                session.finish(START.plusSeconds(5), SessionEndReason.PAUSED);
                yield sessionRepository.saveAndFlush(session).getId();
            }
            case "todo_daily_snapshots" -> snapshotRepository.saveAndFlush(
                    TodoDailySnapshot.capture(todo, SCHEDULE_DATE, false, false, DUE)).getId();
            case "time_estimation_profiles" -> profileRepository.saveAndFlush(
                    TimeEstimationProfile.create(owner, 1, 0, null, 0, 0)).getId();
            case "todo_embeddings" -> embeddingRepository.saveAndFlush(
                    TodoEmbedding.create(todo, vector(), "fixture", "a".repeat(64))).getTodoId();
            case "time_prediction_experiments" -> experimentRepository.saveAndFlush(
                    TimePredictionExperiment.create(UUID.randomUUID(), todo, PredictionStrategy.HYBRID, 10)).getId();
            default -> throw new IllegalArgumentException("알 수 없는 테스트 테이블");
        };
        String pk = (table.equals("user_settings") || table.equals("user_daily_settlement_states")) ? "user_id" : table.equals("todo_embeddings") ? "todo_id" : "id";
        // 테이블·대입식은 아래 고정 사례에서만 제공
        assertViolation(() -> jdbc.update("UPDATE " + table + " SET " + assignment + " WHERE " + pk + "=?", id), state, detail);
    }

    static Stream<Arguments> invalidDatabaseUpdates() {
        return Stream.of(
                Arguments.of("users", "nickname='한'", "23514", "ck_users_nickname_length"),
                Arguments.of("users", "nickname=NULL", "23502", "nickname"),
                Arguments.of("auth_accounts", "user_id=-1", "23503", "fk_auth_accounts_user"),
                Arguments.of("auth_accounts", "provider='UNKNOWN'", "23514", "ck_auth_accounts_"),
                Arguments.of("auth_accounts", "password_hash=NULL", "23514", "ck_auth_accounts_fields"),
                Arguments.of("auth_accounts", "provider_user_id='invalid-local-field'", "23514", "ck_auth_accounts_fields"),
                Arguments.of("auth_accounts", "provider=NULL", "23502", "provider"),
                Arguments.of("user_settings", "user_id=-1", "23503", "fk_user_settings_user"),
                Arguments.of("user_settings", "task_switch_wait_minutes=0", "23514", "ck_user_settings_wait_minutes"),
                Arguments.of("user_settings", "repeat_recommendation_enabled=NULL", "23502", "repeat_recommendation_enabled"),
                Arguments.of("todos", "user_id=-1", "23503", "fk_todos_user"),
                Arguments.of("todos", "title=NULL", "23502", "title"),
                Arguments.of("todos", "ai_estimated_duration_minutes=0", "23514", "ck_todos_ai_estimate_positive"),
                Arguments.of("todos", "user_estimate_source='UNKNOWN'", "23514", "ck_todos_user_estimate_source"),
                Arguments.of("todos", "selected_estimate_type='UNKNOWN'", "23514", "ck_todos_selected_estimate_type"),
                Arguments.of("todos", "status='UNKNOWN'", "23514", "ck_todos_status"),
                Arguments.of("todos", "recurrence_occurrence_at=start_at", "23514", "ck_todos_recurrence_pair"),
                Arguments.of("todos", "all_day=true, start_at=start_at+INTERVAL '1 hour'", "23514", "ck_todos_all_day_boundaries"),
                Arguments.of("sessions", "todo_id=-1", "23503", "fk_sessions_todo"),
                Arguments.of("sessions", "started_at=NULL", "23502", "started_at"),
                Arguments.of("sessions", "ended_at=started_at-INTERVAL '1 second'", "23514", "ck_sessions_time_order"),
                Arguments.of("sessions", "duration_seconds=-1", "23514", "ck_sessions_duration_nonnegative"),
                Arguments.of("sessions", "end_reason='UNKNOWN'", "23514", "ck_sessions_end_reason"),
                Arguments.of("sessions", "ended_at=NULL", "23514", "ck_sessions_end_fields"),
                Arguments.of("todo_daily_snapshots", "todo_id=-1", "23503", "fk_todo_daily_snapshots_todo"),
                Arguments.of("todo_daily_snapshots", "user_id=-1", "23503", "fk_todo_daily_snapshots_user"),
                Arguments.of("todo_daily_snapshots", "schedule_date=NULL", "23502", "schedule_date"),
                Arguments.of("time_estimation_profiles", "user_id=-1", "23503", "fk_time_estimation_profiles_user"),
                Arguments.of("time_estimation_profiles", "duration_correction_factor=NULL", "23502", "duration_correction_factor"),
                Arguments.of("time_estimation_profiles", "duration_correction_factor=0", "23514", "ck_time_estimation_profiles_factor"),
                Arguments.of("time_estimation_profiles", "sample_count=-1", "23514", "ck_time_estimation_profiles_sample_count"),
                Arguments.of("time_estimation_profiles", "log_ratio_stddev=-1", "23514", "ck_time_estimation_profiles_stddev"),
                Arguments.of("time_estimation_profiles", "underestimation_rate=1.1", "23514", "ck_time_estimation_profiles_underestimation_rate"),
                Arguments.of("time_estimation_profiles", "profile_reliability_score=-0.1", "23514", "ck_time_estimation_profiles_reliability"),
                Arguments.of("time_estimation_profiles", "underestimation_rate='NaN'::float8", "23514", "ck_time_estimation_profiles_underestimation_rate"),
                Arguments.of("time_estimation_profiles", "profile_reliability_score='Infinity'::float8", "23514", "ck_time_estimation_profiles_reliability"),
                Arguments.of("todo_embeddings", "todo_id=-1", "23503", "fk_todo_embeddings_todo"),
                Arguments.of("todo_embeddings", "embedding=NULL", "23502", "embedding"),
                Arguments.of("todo_embeddings", "embedding='[1,2]'::vector", "22000", "dimensions"),
                Arguments.of("todo_embeddings", "embedding='[NaN]'::vector", "22000", "NaN"),
                Arguments.of("todo_embeddings", "embedding='[Infinity]'::vector", "22000", "infinite"),
                Arguments.of("todo_embeddings", "embedding_model='   '", "23514", "ck_todo_embeddings_model"),
                Arguments.of("todo_embeddings", "embedding_model=repeat('m',101)", "22001", "character varying(100)"),
                Arguments.of("todo_embeddings", "source_text_hash=repeat('a',63)", "23514", "ck_todo_embeddings_hash"),
                Arguments.of("todo_embeddings", "source_text_hash=repeat('g',64)", "23514", "ck_todo_embeddings_hash"),
                Arguments.of("time_prediction_experiments", "todo_id=-1", "23503", "fk_time_prediction_experiments_todo"),
                Arguments.of("time_prediction_experiments", "user_id=-1", "23503", "fk_time_prediction_experiments_user"),
                Arguments.of("time_prediction_experiments", "prediction_run_id=NULL", "23502", "prediction_run_id"),
                Arguments.of("time_prediction_experiments", "prediction_run_id='invalid'::uuid", "22P02", "uuid"),
                Arguments.of("time_prediction_experiments", "strategy_type='UNKNOWN'", "23514", "ck_time_prediction_experiments_strategy"),
                Arguments.of("time_prediction_experiments", "predicted_minutes=0", "23514", "ck_time_prediction_experiments_predicted"),
                Arguments.of("time_prediction_experiments", "actual_minutes=-1", "23514", "ck_time_prediction_experiments_actual"),
                Arguments.of("time_prediction_experiments", "absolute_error_minutes=-1", "23514", "ck_time_prediction_experiments_absolute_error"),
                Arguments.of("time_prediction_experiments", "absolute_percentage_error=-1", "23514", "ck_time_prediction_experiments_percentage_error"),
                Arguments.of("time_prediction_experiments", "sample_count=-1", "23514", "ck_time_prediction_experiments_sample_count"),
                Arguments.of("time_prediction_experiments", "similar_todo_ids='{}'::jsonb", "23514", "ck_time_prediction_experiments_json_arrays"),
                Arguments.of("time_prediction_experiments", "reason_codes='null'::jsonb", "23514", "ck_time_prediction_experiments_json_arrays")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"profile", "embedding", "local-email", "social-identity", "user-local", "user-social"})
    void rejectsUniqueDataLayerConflicts(String kind) {
        User owner = persistUser("중복사용자");
        User other = persistUser("중복다른이");
        Todo todo = persistTodo(owner, null);
        switch (kind) {
            case "profile" -> {
                var first = profileRepository.saveAndFlush(TimeEstimationProfile.create(owner, 1, 0, null, 0, 0));
                assertViolation(() -> jdbc.update("""
                        INSERT INTO time_estimation_profiles (user_id, duration_correction_factor,
                            sample_count, underestimation_rate, profile_reliability_score)
                        SELECT user_id, duration_correction_factor, sample_count, underestimation_rate,
                            profile_reliability_score FROM time_estimation_profiles WHERE id=?
                        """, first.getId()), "23505", "uq_time_estimation_profiles_user");
            }
            case "embedding" -> {
                embeddingRepository.saveAndFlush(TodoEmbedding.create(todo, vector(), "fixture", "a".repeat(64)));
                assertViolation(() -> jdbc.update("""
                        INSERT INTO todo_embeddings (todo_id, embedding, embedding_model, source_text_hash)
                        SELECT todo_id, embedding, embedding_model, source_text_hash FROM todo_embeddings WHERE todo_id=?
                        """, todo.getId()), "23505", "todo_embeddings_pkey");
            }
            case "local-email", "user-local" -> {
                authRepository.saveAndFlush(AuthAccount.local(owner, "unique@atch.invalid", UUID.randomUUID().toString()));
                assertViolation(() -> jdbc.update("""
                        INSERT INTO auth_accounts (user_id, provider, email, password_hash)
                        VALUES (?, 'LOCAL', ?, ?)
                        """, kind.equals("local-email") ? other.getId() : owner.getId(),
                        kind.equals("local-email") ? "UNIQUE@ATCH.INVALID" : "different@atch.invalid",
                        UUID.randomUUID().toString()), "23505",
                        kind.equals("local-email") ? "ux_auth_accounts_local_email" : "ux_auth_accounts_user_local");
            }
            case "social-identity", "user-social" -> {
                authRepository.saveAndFlush(AuthAccount.social(owner, AuthProvider.GOOGLE, null, "unique-social"));
                assertViolation(() -> jdbc.update("""
                        INSERT INTO auth_accounts (user_id, provider, provider_user_id)
                        VALUES (?, ?, ?)
                        """, kind.equals("social-identity") ? other.getId() : owner.getId(),
                        kind.equals("social-identity") ? "GOOGLE" : "KAKAO",
                        kind.equals("social-identity") ? "unique-social" : "different-social"), "23505",
                        kind.equals("social-identity") ? "ux_auth_accounts_social_identity" : "ux_auth_accounts_user_social");
            }
        }
    }

    @Test
    void reloadsEveryTodoStatusAsItsStringEnum() {
        User owner = persistUser("상태사용자");
        Todo todo = persistTodo(owner, null);
        for (TodoStatus status : TodoStatus.values()) {
            // 상태 전환 API가 아닌 Enum 저장·조회 픽스처
            jdbc.update("UPDATE todos SET status=? WHERE id=?", status.name(), todo.getId());
            entityManager.clear();
            assertThat(todoRepository.findByIdAndUser_IdAndDeletedAtIsNull(todo.getId(), owner.getId())
                    .orElseThrow().getStatus()).isEqualTo(status);
        }
    }

    @Test
    void documentsHistoryQueriesThatCurrentlyIncludeSoftDeletedTodos() {
        User owner = persistUser("삭제이력검사");
        Todo todo = persistTodo(owner, null);
        sessionRepository.saveAndFlush(Session.start(todo, START));
        embeddingRepository.saveAndFlush(TodoEmbedding.create(todo, vector(), "fixture", "a".repeat(64)));
        var experiment = experimentRepository.saveAndFlush(TimePredictionExperiment.create(
                UUID.randomUUID(), todo, PredictionStrategy.HYBRID, 10));
        jdbc.update("UPDATE todos SET deleted_at=? WHERE id=?", Timestamp.from(DUE), todo.getId());
        entityManager.clear();
        assertThat(todoRepository.findByIdAndUser_IdAndDeletedAtIsNull(todo.getId(), owner.getId())).isEmpty();
        assertThat(sessionRepository.findByTodo_IdAndTodo_User_IdOrderByStartedAtAscIdAsc(todo.getId(), owner.getId())).hasSize(1);
        assertThat(embeddingRepository.findByTodo_IdAndTodo_User_Id(todo.getId(), owner.getId())).isPresent();
        assertThat(experimentRepository.findByTodo_IdAndUser_IdOrderByCreatedAtDescIdDesc(todo.getId(), owner.getId()))
                .extracting(TimePredictionExperiment::getId).containsExactly(experiment.getId());
    }

    @Test
    void savesRecurrenceGraphAndQueriesOwnedPatternsAndRules() {
        User owner = persistUser("반복소유자");
        User other = persistUser("다른반복자");
        RepeatPattern daily = persistPattern(owner, RecurrenceFrequency.DAILY);
        RepeatPattern weekly = persistPattern(owner, RecurrenceFrequency.WEEKLY);
        RepeatPattern recent = persistPattern(owner, RecurrenceFrequency.DAILY);
        RepeatPattern foreign = persistPattern(other, RecurrenceFrequency.WEEKLY);
        RepeatPattern unused = persistPattern(owner, RecurrenceFrequency.DAILY);
        RecurrenceRule active = persistRule(owner, daily);
        RecurrenceRule disabled = persistRule(owner, weekly);
        RecurrenceRule deleted = persistRule(owner, recent);
        persistRule(other, foreign);
        Todo todo = persistTodo(owner, active.getId());

        assertThat(daily.getStatus()).isEqualTo(RepeatPatternStatus.DETECTING);
        assertThat(daily.getLastSuggestedAt()).isNull();
        assertThat(active.isEnabled()).isTrue();
        assertThat(active.getDeletedAt()).isNull();
        assertThat(active.getFrequency()).isEqualTo(RecurrenceFrequency.DAILY);
        assertThat(disabled.getFrequency()).isEqualTo(RecurrenceFrequency.WEEKLY);

        // 상태 변경·삭제 기능의 구현이 아닌 Repository 조회용 SQL 픽스처
        jdbc.update("""
                UPDATE repeat_patterns SET status='SUGGESTED', last_suggested_at=?
                WHERE id IN (?, ?, ?)
                """, Timestamp.from(START), daily.getId(), weekly.getId(), foreign.getId());
        jdbc.update("UPDATE repeat_patterns SET status='SUGGESTED', last_suggested_at=? WHERE id=?",
                Timestamp.from(START.plusSeconds(60)), recent.getId());
        jdbc.update("UPDATE recurrence_rules SET enabled=false WHERE id=?", disabled.getId());
        jdbc.update("UPDATE recurrence_rules SET deleted_at=? WHERE id=?",
                Timestamp.from(DUE), deleted.getId());
        entityManager.clear();

        RepeatPattern reloaded = patternRepository.findByIdAndUser_Id(weekly.getId(), owner.getId())
                .orElseThrow();
        assertThat(reloaded.getUser().getId()).isEqualTo(owner.getId());
        assertThat(reloaded.getFrequency()).isEqualTo(RecurrenceFrequency.WEEKLY);
        assertThat(reloaded.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(reloaded.getScheduledTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(reloaded.getOccurrenceCount()).isEqualTo(3);
        assertThat(reloaded.getLastOccurrenceDate()).isEqualTo(SCHEDULE_DATE);
        assertThat(reloaded.getLastSuggestedAt()).isEqualTo(START);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(patternRepository.findByIdAndUser_Id(weekly.getId(), other.getId())).isEmpty();
        assertThat(patternRepository.findAllByUser_IdAndStatusOrderByLastSuggestedAtDescIdDesc(
                owner.getId(), RepeatPatternStatus.SUGGESTED))
                .extracting(RepeatPattern::getId).containsExactly(recent.getId(), weekly.getId(), daily.getId());

        RecurrenceRule reloadedRule = ruleRepository
                .findByIdAndUser_IdAndDeletedAtIsNull(active.getId(), owner.getId()).orElseThrow();
        assertThat(reloadedRule.getSourcePattern().getId()).isEqualTo(daily.getId());
        assertThat(reloadedRule.getUser().getId()).isEqualTo(owner.getId());
        assertThat(reloadedRule.getDayOfWeek()).isNull();
        assertThat(reloadedRule.getScheduledTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(reloadedRule.getTaskName()).isEqualTo("반복 작업");
        assertThat(reloadedRule.getMemo()).isEqualTo("검증 메모");
        assertThat(reloadedRule.getDefaultDeadlineOffsetMinutes()).isEqualTo(60);
        assertThat(reloadedRule.getDefaultUserEstimatedDurationMinutes()).isEqualTo(30);
        assertThat(reloadedRule.getCreatedAt()).isNotNull();
        assertThat(reloadedRule.getUpdatedAt()).isNotNull();
        assertThat(ruleRepository.findByIdAndUser_IdAndDeletedAtIsNull(active.getId(), other.getId())).isEmpty();
        assertThat(ruleRepository.findByIdAndUser_IdAndDeletedAtIsNull(deleted.getId(), owner.getId())).isEmpty();
        assertThat(ruleRepository.findAllByUser_IdAndDeletedAtIsNullOrderByIdDesc(owner.getId()))
                .extracting(RecurrenceRule::getId).containsExactly(disabled.getId(), active.getId());
        assertThat(ruleRepository.findAllByUser_IdAndEnabledTrueAndDeletedAtIsNullOrderByIdAsc(owner.getId()))
                .extracting(RecurrenceRule::getId).containsExactly(active.getId());
        assertThat(ruleRepository.existsBySourcePattern_Id(recent.getId())).isTrue();
        assertThat(ruleRepository.existsBySourcePattern_Id(unused.getId())).isFalse();

        Todo reloadedTodo = todoRepository.findByIdAndUser_IdAndDeletedAtIsNull(todo.getId(), owner.getId())
                .orElseThrow();
        assertThat(reloadedTodo.getRecurrenceRuleId()).isEqualTo(active.getId());
        assertThat(reloadedTodo.getRecurrenceOccurrenceAt()).isEqualTo(START);
        assertThat(reloadedTodo.getRecurrencePeriodStartDate()).isEqualTo(SCHEDULE_DATE);
        assertThat(reloadedTodo.getUserEstimateSource()).isEqualTo(UserEstimateSource.RECURRENCE_TEMPLATE);
        assertThat(todoRepository.findByIdAndUser_IdAndDeletedAtIsNull(todo.getId(), other.getId())).isEmpty();
    }

    @Test
    void rejectsInvalidRecurrenceConstructionBeforePersistence() {
        User owner = persistUser("생성검사자");
        User other = persistUser("다른생성자");
        RepeatPattern pattern = persistPattern(owner, RecurrenceFrequency.DAILY);
        assertThrows(IllegalArgumentException.class, () -> ruleBuilder(other, pattern).build());
        assertThrows(IllegalArgumentException.class, () -> patternBuilder(owner, RecurrenceFrequency.DAILY)
                .dayOfWeek(DayOfWeek.MONDAY).build());
        assertThrows(IllegalArgumentException.class, () -> patternBuilder(owner, RecurrenceFrequency.WEEKLY)
                .dayOfWeek(null).build());
        assertThrows(IllegalArgumentException.class, () -> patternBuilder(owner, RecurrenceFrequency.DAILY)
                .occurrenceCount(0).build());
        assertThrows(IllegalArgumentException.class, () -> patternBuilder(owner, RecurrenceFrequency.DAILY)
                .scheduledTime(null).build());
        assertThrows(IllegalArgumentException.class, () -> ruleBuilder(owner, pattern)
                .dayOfWeek(DayOfWeek.MONDAY).build());
        assertThrows(IllegalArgumentException.class, () -> ruleBuilder(owner, pattern)
                .defaultDeadlineOffsetMinutes(0).build());
        assertThrows(IllegalArgumentException.class, () -> ruleBuilder(owner, pattern)
                .defaultUserEstimatedDurationMinutes(0).build());
        assertThrows(NullPointerException.class, () -> ruleBuilder(owner, pattern).sourcePattern(null).build());
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("recurrenceViolations")
    void rejectsRecurrenceIntegrityViolations(String kind, String assignment, String sqlState, String constraint) {
        User owner = persistUser("반복제약자");
        User other = persistUser("다른제약자");
        RepeatPattern pattern = persistPattern(owner, RecurrenceFrequency.DAILY);
        RecurrenceRule rule = persistRule(owner, pattern);
        switch (kind) {
            case "rule-owner" -> assertViolation(() -> jdbc.update(
                    "UPDATE recurrence_rules SET user_id=? WHERE id=?", other.getId(), rule.getId()),
                    sqlState, constraint);
            case "rule-source", "rule-source-deleted" -> {
                if (kind.endsWith("deleted")) {
                    jdbc.update("UPDATE recurrence_rules SET deleted_at=? WHERE id=?",
                            Timestamp.from(DUE), rule.getId());
                }
                assertViolation(() -> jdbc.update("""
                        INSERT INTO recurrence_rules (user_id, source_pattern_id, task_name, frequency,
                            scheduled_time, default_deadline_offset_minutes,
                            default_user_estimated_duration_minutes, enabled)
                        SELECT user_id, source_pattern_id, task_name, frequency, scheduled_time,
                            default_deadline_offset_minutes, default_user_estimated_duration_minutes, enabled
                        FROM recurrence_rules WHERE id=?
                        """, rule.getId()), sqlState, constraint);
            }
            case "pattern-day", "rule-day" -> {
                String table = kind.equals("pattern-day") ? "repeat_patterns" : "recurrence_rules";
                Long id = kind.equals("pattern-day") ? pattern.getId() : rule.getId();
                assertViolation(() -> jdbc.update("UPDATE " + table + " SET " + assignment + " WHERE id=?", id),
                        sqlState, constraint);
            }
            case "todo-owner", "todo-null", "period", "period-deleted" -> {
                Todo todo = persistTodo(owner, rule.getId());
                if (kind.equals("todo-owner")) {
                    assertViolation(() -> jdbc.update("UPDATE todos SET user_id=? WHERE id=?",
                            other.getId(), todo.getId()), sqlState, constraint);
                } else if (kind.equals("todo-null")) {
                    assertViolation(() -> jdbc.update("UPDATE todos SET " + assignment + " WHERE id=?",
                            todo.getId()), sqlState, constraint);
                } else {
                    if (kind.endsWith("deleted")) {
                        jdbc.update("UPDATE todos SET deleted_at=? WHERE id=?", Timestamp.from(DUE), todo.getId());
                    }
                    // 기존 시각 UNIQUE에 가려지지 않도록 최초 예정 시각만 다르게 지정
                    assertViolation(() -> jdbc.update("""
                            INSERT INTO todos (user_id, recurrence_rule_id, recurrence_occurrence_at,
                                recurrence_period_start_date, title, start_at, due_at, current_schedule_date,
                                all_day, user_estimated_duration_minutes, user_estimate_source,
                                selected_estimate_type, status)
                            SELECT user_id, recurrence_rule_id, recurrence_occurrence_at + INTERVAL '1 minute',
                                recurrence_period_start_date, title, start_at, due_at, current_schedule_date,
                                all_day, user_estimated_duration_minutes, user_estimate_source,
                                selected_estimate_type, status FROM todos WHERE id=?
                            """, todo.getId()), sqlState, constraint);
                }
            }
            default -> throw new IllegalArgumentException("알 수 없는 제약 사례: " + kind);
        }
    }

    static Stream<Arguments> recurrenceViolations() {
        return Stream.of(
                Arguments.of("rule-owner", "", "23503", "fk_recurrence_rules_pattern_owner"),
                Arguments.of("todo-owner", "", "23503", "fk_todos_recurrence_rule_owner"),
                Arguments.of("rule-source", "", "23505", "uq_recurrence_rules_source_pattern"),
                Arguments.of("rule-source-deleted", "", "23505", "uq_recurrence_rules_source_pattern"),
                Arguments.of("period", "", "23505", "uq_todos_recurrence_period"),
                Arguments.of("period-deleted", "", "23505", "uq_todos_recurrence_period"),
                Arguments.of("todo-null", "recurrence_rule_id=NULL, recurrence_occurrence_at=NULL",
                        "23514", "ck_todos_recurrence_period_pair"),
                Arguments.of("todo-null", "recurrence_rule_id=NULL, recurrence_period_start_date=NULL",
                        "23514", "ck_todos_recurrence_pair"),
                Arguments.of("todo-null", "recurrence_rule_id=NULL", "23514", "ck_todos_recurrence_p"),
                Arguments.of("todo-null", "recurrence_occurrence_at=NULL, recurrence_period_start_date=NULL",
                        "23514", "ck_todos_recurrence_p"),
                Arguments.of("todo-null", "recurrence_occurrence_at=NULL", "23514", "ck_todos_recurrence_pair"),
                Arguments.of("todo-null", "recurrence_period_start_date=NULL", "23514", "ck_todos_recurrence_period_pair"),
                Arguments.of("pattern-day", "day_of_week='MONDAY'", "23514", "ck_repeat_patterns_day_of_week"),
                Arguments.of("pattern-day", "frequency='WEEKLY', day_of_week=NULL", "23514", "ck_repeat_patterns_day_of_week"),
                Arguments.of("pattern-day", "frequency='WEEKLY', day_of_week='FUNDAY'", "23514", "ck_repeat_patterns_day_of_week"),
                Arguments.of("rule-day", "day_of_week='MONDAY'", "23514", "ck_recurrence_rules_day_of_week"),
                Arguments.of("rule-day", "frequency='WEEKLY', day_of_week=NULL", "23514", "ck_recurrence_rules_day_of_week"),
                Arguments.of("rule-day", "frequency='WEEKLY', day_of_week='FUNDAY'", "23514", "ck_recurrence_rules_day_of_week")
        );
    }

    private RepeatPattern.RepeatPatternBuilder patternBuilder(User owner, RecurrenceFrequency frequency) {
        return RepeatPattern.builder().user(owner).taskName("반복 작업").frequency(frequency)
                .dayOfWeek(frequency == RecurrenceFrequency.WEEKLY ? DayOfWeek.WEDNESDAY : null)
                .scheduledTime(LocalTime.of(9, 30)).occurrenceCount(3).lastOccurrenceDate(SCHEDULE_DATE);
    }

    private RepeatPattern persistPattern(User owner, RecurrenceFrequency frequency) {
        return patternRepository.saveAndFlush(patternBuilder(owner, frequency).build());
    }

    private RecurrenceRule.RecurrenceRuleBuilder ruleBuilder(User owner, RepeatPattern pattern) {
        return RecurrenceRule.builder().user(owner).sourcePattern(pattern).taskName("반복 작업").memo("검증 메모")
                .dayOfWeek(pattern == null ? null : pattern.getDayOfWeek()).scheduledTime(LocalTime.of(9, 30))
                .defaultDeadlineOffsetMinutes(60).defaultUserEstimatedDurationMinutes(30);
    }

    private RecurrenceRule persistRule(User owner, RepeatPattern pattern) {
        return ruleRepository.saveAndFlush(ruleBuilder(owner, pattern).build());
    }

    @Test
    void savesRewardGraphAndQueriesOwnedItemsEquipmentSettlementsAndGrants() {
        User owner = persistUser("보상소유자");
        User other = persistUser("다른보상자");
        CharacterItem b = persistItem(ItemGrade.B, ItemCategory.COLOR);
        CharacterItem extraB = persistItem(ItemGrade.B, ItemCategory.EYES);
        CharacterItem a = persistItem(ItemGrade.A, ItemCategory.BACKGROUND);
        CharacterItem s = persistItem(ItemGrade.S, ItemCategory.ACCESSORY);
        var owned = ownedRepository.saveAllAndFlush(List.of(
                UserCharacterItem.acquire(owner, b), UserCharacterItem.acquire(owner, a),
                UserCharacterItem.acquire(owner, s)));
        ownedRepository.saveAndFlush(UserCharacterItem.acquire(other, extraB));
        UserEquippedItem equipped = equippedRepository.saveAndFlush(UserEquippedItem.equip(owner, b, START));
        equippedRepository.saveAndFlush(UserEquippedItem.equip(owner, a, START.plusSeconds(1)));
        equippedRepository.saveAndFlush(UserEquippedItem.equip(other, extraB, START));
        RewardSettlement settlement = settlementRepository.saveAndFlush(
                RewardSettlement.settle(owner, SCHEDULE_DATE, 4, 4, DUE));
        // 추첨·등급 판정 기능과 구분하는 저장·조회용 명시적 지급 픽스처
        grantRepository.saveAllAndFlush(List.of(
                RewardGrant.fromSettlement(settlement, b, DUE),
                RewardGrant.fromSettlement(settlement, a, DUE),
                RewardGrant.fromSettlement(settlement, s, DUE)));
        settlementRepository.saveAndFlush(RewardSettlement.settle(owner, SCHEDULE_DATE.plusDays(1), 0, 0, DUE));

        assertThat(b.isActive()).isTrue();
        assertThat(owned).allSatisfy(value -> assertThat(value.getAcquiredAt()).isNotNull());
        var acquiredAt = owned.getFirst().getAcquiredAt();
        // 비활성화 기능 구현이 아닌 기존 보유·장착 보존 확인용 SQL 픽스처
        jdbc.update("UPDATE character_items SET active=false WHERE id=?", b.getId());
        entityManager.clear();

        CharacterItem reloadedItem = itemRepository.findById(b.getId()).orElseThrow();
        assertThat(reloadedItem.getName()).isEqualTo("검증 아이템");
        assertThat(reloadedItem.getAssetKey()).isEqualTo("fixture/B/COLOR");
        assertThat(reloadedItem.getGrade()).isEqualTo(ItemGrade.B);
        assertThat(reloadedItem.getCategory()).isEqualTo(ItemCategory.COLOR);
        assertThat(reloadedItem.getCreatedAt()).isNotNull();
        assertThat(reloadedItem.getUpdatedAt()).isNotNull();
        assertThat(reloadedItem.isActive()).isFalse();
        assertThat(itemRepository.findAllByGradeAndActiveTrueOrderByIdAsc(ItemGrade.B))
                .extracting(CharacterItem::getId).containsExactly(extraB.getId());
        assertThat(itemRepository.countByActiveTrue()).isEqualTo(3);
        assertThat(ownedRepository.countByUser_IdAndCharacterItem_ActiveTrue(owner.getId())).isEqualTo(2);
        assertThat(ownedRepository.countByUser_IdAndCharacterItem_ActiveTrue(other.getId())).isEqualTo(1);
        assertThat(ownedRepository.existsByUser_IdAndCharacterItem_Id(owner.getId(), b.getId())).isTrue();
        assertThat(ownedRepository.existsByUser_IdAndCharacterItem_Id(other.getId(), b.getId())).isFalse();
        var reloadedOwned = ownedRepository.findAllByUser_IdOrderByAcquiredAtDescIdDesc(owner.getId());
        assertThat(reloadedOwned).hasSize(3).isSortedAccordingTo(Comparator
                .comparing(UserCharacterItem::getAcquiredAt).thenComparing(UserCharacterItem::getId).reversed());
        assertThat(reloadedOwned).extracting(value -> value.getUser().getId()).containsOnly(owner.getId());
        assertThat(ownedRepository.findById(owned.getFirst().getId()).orElseThrow().getAcquiredAt())
                .isEqualTo(acquiredAt);
        UserEquippedItem reloadedEquipment = equippedRepository
                .findByUser_IdAndCategory(owner.getId(), ItemCategory.COLOR).orElseThrow();
        assertThat(reloadedEquipment.getId()).isEqualTo(equipped.getId());
        assertThat(reloadedEquipment.getCharacterItem().getId()).isEqualTo(b.getId());
        assertThat(reloadedEquipment.getCharacterItem().isActive()).isFalse();
        assertThat(reloadedEquipment.getEquippedAt()).isEqualTo(START);
        assertThat(reloadedEquipment.getUpdatedAt()).isNotNull();
        assertThat(equippedRepository.findByUser_IdAndCategory(other.getId(), ItemCategory.COLOR)).isEmpty();
        assertThat(equippedRepository.findAllByUser_IdOrderByCategoryAsc(owner.getId()))
                .extracting(UserEquippedItem::getCategory).containsExactly(ItemCategory.BACKGROUND, ItemCategory.COLOR);
        RewardSettlement reloadedSettlement = settlementRepository
                .findByUser_IdAndRewardDate(owner.getId(), SCHEDULE_DATE).orElseThrow();
        assertThat(reloadedSettlement.getAssignedTodoCount()).isEqualTo(4);
        assertThat(reloadedSettlement.getCompletedTodoCount()).isEqualTo(4);
        assertThat(reloadedSettlement.getCompletionRate()).isEqualByComparingTo("100.00");
        assertThat(reloadedSettlement.getCompletionRate().scale()).isEqualTo(2);
        assertThat(reloadedSettlement.getSettledAt()).isEqualTo(DUE);
        assertThat(settlementRepository.existsByUser_IdAndRewardDate(owner.getId(), SCHEDULE_DATE)).isTrue();
        assertThat(settlementRepository.findByUser_IdAndRewardDate(other.getId(), SCHEDULE_DATE)).isEmpty();
        var grants = grantRepository.findAllByUser_IdAndRewardDateOrderByRewardGradeAsc(owner.getId(), SCHEDULE_DATE);
        assertThat(grants).extracting(RewardGrant::getRewardGrade).containsExactly(ItemGrade.A, ItemGrade.B, ItemGrade.S);
        assertThat(grants).allSatisfy(grant -> {
            assertThat(grant.getUser().getId()).isEqualTo(owner.getId());
            assertThat(grant.getRewardDate()).isEqualTo(SCHEDULE_DATE);
            assertThat(grant.getCompletionRate()).isEqualByComparingTo(reloadedSettlement.getCompletionRate());
            assertThat(grant.getCharacterItem().getGrade()).isEqualTo(grant.getRewardGrade());
            assertThat(grant.getGrantedAt()).isEqualTo(DUE);
        });
        assertThat(grantRepository.existsByUser_IdAndRewardDateAndRewardGrade(owner.getId(), SCHEDULE_DATE, ItemGrade.B))
                .isTrue();
        assertThat(grantRepository.existsByUser_IdAndRewardDateAndRewardGrade(other.getId(), SCHEDULE_DATE, ItemGrade.B))
                .isFalse();
        assertThat(settlementRepository.findByUser_IdAndRewardDate(owner.getId(), SCHEDULE_DATE.plusDays(1))
                .orElseThrow().getCompletionRate()).isEqualByComparingTo("0.00");
        assertThat(grantRepository.findAllByUser_IdAndRewardDateOrderByRewardGradeAsc(owner.getId(), SCHEDULE_DATE.plusDays(1)))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("completionRates")
    void calculatesSettlementPercentageWithHalfUpRounding(int assigned, int completed, String expected) {
        var settlement = RewardSettlement.settle(new User("완료율"), SCHEDULE_DATE, assigned, completed, DUE);
        assertThat(settlement.getCompletionRate()).isEqualByComparingTo(new BigDecimal(expected));
        assertThat(settlement.getCompletionRate().scale()).isEqualTo(2);
        assertThat(settlement.getAssignedTodoCount()).isEqualTo(assigned);
        assertThat(settlement.getCompletedTodoCount()).isEqualTo(completed);
    }

    static Stream<Arguments> completionRates() {
        return Stream.of(Arguments.of(0, 0, "0.00"), Arguments.of(3, 2, "66.67"),
                Arguments.of(32, 1, "3.13"), Arguments.of(20000, 19999, "100.00"));
    }

    @Test
    void rejectsInvalidSettlementCountsAndMissingRewardInputs() {
        User owner = new User("입력검사");
        assertThrows(IllegalArgumentException.class, () -> RewardSettlement.settle(owner, SCHEDULE_DATE, -1, 0, DUE));
        assertThrows(IllegalArgumentException.class, () -> RewardSettlement.settle(owner, SCHEDULE_DATE, 1, -1, DUE));
        assertThrows(IllegalArgumentException.class, () -> RewardSettlement.settle(owner, SCHEDULE_DATE, 1, 2, DUE));
        assertThrows(NullPointerException.class, () -> RewardSettlement.settle(owner, null, 0, 0, DUE));
        assertThrows(NullPointerException.class, () -> UserCharacterItem.acquire(owner, null));
        assertThrows(NullPointerException.class, () -> UserEquippedItem.equip(owner, null, START));
        assertThrows(NullPointerException.class, () -> RewardGrant.fromSettlement(null, null, DUE));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("rewardViolations")
    void rejectsRewardIntegrityViolations(String kind, String assignment, String sqlState, String constraint) {
        User owner = persistUser("보상제약자");
        User other = persistUser("타인제약자");
        CharacterItem item = persistItem(ItemGrade.B, ItemCategory.COLOR);
        CharacterItem extra = persistItem(ItemGrade.B, ItemCategory.COLOR);
        ownedRepository.saveAndFlush(UserCharacterItem.acquire(owner, item));
        var settlement = settlementRepository.saveAndFlush(RewardSettlement.settle(owner, SCHEDULE_DATE, 4, 3, DUE));
        switch (kind) {
            case "owned" -> assertViolation(() -> jdbc.update("""
                    INSERT INTO user_character_items(user_id, character_item_id) VALUES (?, ?)
                    """, owner.getId(), item.getId()), sqlState, constraint);
            case "equipment-duplicate" -> {
                ownedRepository.saveAndFlush(UserCharacterItem.acquire(owner, extra));
                equippedRepository.saveAndFlush(UserEquippedItem.equip(owner, item, START));
                assertViolation(() -> jdbc.update("""
                        INSERT INTO user_equipped_items(user_id, character_item_id, category, equipped_at)
                        VALUES (?, ?, 'COLOR', ?)
                        """, owner.getId(), extra.getId(), Timestamp.from(START)), sqlState, constraint);
            }
            case "equipment-unowned", "equipment-other", "equipment-category" -> {
                if (kind.equals("equipment-other")) {
                    ownedRepository.saveAndFlush(UserCharacterItem.acquire(other, extra));
                }
                long targetItem = kind.equals("equipment-category") ? item.getId() : extra.getId();
                String category = kind.equals("equipment-category") ? "EYES" : "COLOR";
                assertViolation(() -> jdbc.update("""
                        INSERT INTO user_equipped_items(user_id, character_item_id, category, equipped_at)
                        VALUES (?, ?, ?, ?)
                        """, owner.getId(), targetItem, category, Timestamp.from(START)), sqlState, constraint);
            }
            case "settlement-duplicate" -> assertViolation(() -> jdbc.update("""
                    INSERT INTO reward_settlements(user_id, reward_date, assigned_todo_count,
                        completed_todo_count, completion_rate, settled_at) VALUES (?, ?, 0, 0, 0, ?)
                    """, owner.getId(), SCHEDULE_DATE, Timestamp.from(DUE)), sqlState, constraint);
            case "grant-grade-duplicate", "grant-item-duplicate" -> {
                grantRepository.saveAndFlush(RewardGrant.fromSettlement(settlement, item, DUE));
                LocalDate date = SCHEDULE_DATE;
                if (kind.equals("grant-item-duplicate")) {
                    date = date.plusDays(1);
                    settlementRepository.saveAndFlush(RewardSettlement.settle(owner, date, 4, 3, DUE));
                }
                LocalDate targetDate = date;
                long targetItem = kind.equals("grant-item-duplicate") ? item.getId() : extra.getId();
                assertViolation(() -> jdbc.update("""
                        INSERT INTO reward_grants(user_id, character_item_id, reward_date,
                            reward_grade, completion_rate, granted_at) VALUES (?, ?, ?, 'B', 75, ?)
                        """, owner.getId(), targetItem, targetDate, Timestamp.from(DUE)), sqlState, constraint);
            }
            case "grant-grade", "grant-no-settlement" -> {
                String grade = kind.equals("grant-grade") ? "A" : "B";
                LocalDate date = kind.equals("grant-no-settlement") ? SCHEDULE_DATE.plusDays(1) : SCHEDULE_DATE;
                assertViolation(() -> jdbc.update("""
                        INSERT INTO reward_grants(user_id, character_item_id, reward_date,
                            reward_grade, completion_rate, granted_at) VALUES (?, ?, ?, ?, 75, ?)
                        """, owner.getId(), item.getId(), date, grade, Timestamp.from(DUE)), sqlState, constraint);
            }
            case "settlement-update" -> assertViolation(() -> jdbc.update(
                    "UPDATE reward_settlements SET " + assignment + " WHERE id=?", settlement.getId()), sqlState, constraint);
            case "grant-update" -> {
                var grant = grantRepository.saveAndFlush(RewardGrant.fromSettlement(settlement, item, DUE));
                assertViolation(() -> jdbc.update("UPDATE reward_grants SET " + assignment + " WHERE id=?", grant.getId()),
                        sqlState, constraint);
            }
            case "catalog-update" -> assertViolation(() -> jdbc.update(
                    "UPDATE character_items SET " + assignment + " WHERE id=?", item.getId()), sqlState, constraint);
            default -> throw new IllegalArgumentException("알 수 없는 보상 제약 사례: " + kind);
        }
    }

    static Stream<Arguments> rewardViolations() {
        return Stream.of(
                Arguments.of("owned", "", "23505", "uq_user_character_items_user_item"),
                Arguments.of("equipment-duplicate", "", "23505", "uq_user_equipped_items_user_category"),
                Arguments.of("equipment-unowned", "", "23503", "fk_user_equipped_items_owned"),
                Arguments.of("equipment-other", "", "23503", "fk_user_equipped_items_owned"),
                Arguments.of("equipment-category", "", "23503", "fk_user_equipped_items_item_category"),
                Arguments.of("settlement-duplicate", "", "23505", "uq_reward_settlements_user_date"),
                Arguments.of("grant-grade-duplicate", "", "23505", "uq_reward_grants_user_date_grade"),
                Arguments.of("grant-item-duplicate", "", "23505", "uq_reward_grants_user_item"),
                Arguments.of("grant-grade", "", "23503", "fk_reward_grants_item_grade"),
                Arguments.of("grant-no-settlement", "", "23503", "fk_reward_grants_settlement"),
                Arguments.of("settlement-update", "assigned_todo_count=-1", "23514", "ck_reward_settlements_todo_counts"),
                Arguments.of("settlement-update", "completed_todo_count=-1", "23514", "ck_reward_settlements_todo_counts"),
                Arguments.of("settlement-update", "completed_todo_count=5", "23514", "ck_reward_settlements_todo_counts"),
                Arguments.of("settlement-update", "completion_rate=-0.01", "23514", "ck_reward_settlements_completion_rate"),
                Arguments.of("settlement-update", "completion_rate=100.01", "23514", "ck_reward_settlements_completion_rate"),
                Arguments.of("grant-update", "completion_rate=-0.01", "23514", "ck_reward_grants_completion_rate"),
                Arguments.of("grant-update", "completion_rate=100.01", "23514", "ck_reward_grants_completion_rate"),
                Arguments.of("catalog-update", "grade='X'", "23514", "ck_character_items_grade"),
                Arguments.of("catalog-update", "category='HAT'", "23514", "ck_character_items_category")
        );
    }

    private CharacterItem persistItem(ItemGrade grade, ItemCategory category) {
        return itemRepository.saveAndFlush(CharacterItem.builder().name("검증 아이템")
                .grade(grade).category(category).assetKey("fixture/" + grade + "/" + category).build());
    }

    private float[] vector() {
        float[] values = new float[1024];
        for (int i = 0; i < values.length; i++) values[i] = (i % 17 - 8) / 16.0f;
        return values;
    }

    private Todo transientTodo(User owner) {
        return Todo.builder().user(owner).title("미저장 작업").startAt(START).dueAt(DUE)
                .currentScheduleDate(SCHEDULE_DATE).userEstimatedDurationMinutes(10)
                .userEstimateSource(UserEstimateSource.USER_INPUT).selectedEstimateType(SelectedEstimateType.USER).build();
    }

    private User persistUser(String nickname) {
        User user = new User(nickname);
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Todo persistTodo(User owner, Long recurrenceRuleId) {
        return todoRepository.saveAndFlush(Todo.builder()
                .user(owner)
                .recurrenceRuleId(recurrenceRuleId)
                .recurrenceOccurrenceAt(recurrenceRuleId == null ? null : START)
                .recurrencePeriodStartDate(recurrenceRuleId == null ? null : SCHEDULE_DATE)
                .title("검증 작업")
                .startAt(START)
                .dueAt(DUE)
                .currentScheduleDate(SCHEDULE_DATE)
                .userEstimatedDurationMinutes(30)
                .userEstimateSource(recurrenceRuleId == null
                        ? UserEstimateSource.USER_INPUT : UserEstimateSource.RECURRENCE_TEMPLATE)
                .selectedEstimateType(SelectedEstimateType.USER)
                .build());
    }

    private void assertViolation(Runnable statement, String sqlState, String constraint) {
        var exception = assertThrows(DataIntegrityViolationException.class, statement::run);
        assertThat(exception.getMostSpecificCause()).isInstanceOf(SQLException.class);
        var cause = (SQLException) exception.getMostSpecificCause();
        assertThat(cause.getSQLState()).isEqualTo(sqlState);
        assertThat(cause.getMessage()).contains(constraint);
    }
}
