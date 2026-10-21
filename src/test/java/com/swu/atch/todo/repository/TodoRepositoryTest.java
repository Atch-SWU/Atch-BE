package com.swu.atch.todo.repository;

import com.swu.atch.AtchApplication;
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
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
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
    ).withDatabaseName("atch_todo_test");

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
    @EnableJpaRepositories(basePackageClasses = TodoRepository.class)
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
                if (flyway.getConfiguration().getDataSource() != dataSource) {
                    throw new IllegalStateException("Flyway 테스트 DataSource가 일치하지 않습니다.");
                }
                flyway.migrate();
            };
        }
    }

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void migratesV1ThroughV4AndValidatesJpaOnTheContainer() throws SQLException {
        assertThat(flyway.getConfiguration().getDataSource()).isSameAs(dataSource);
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(POSTGRES.getJdbcUrl());
            assertThat(connection.getCatalog()).isEqualTo("atch_todo_test");
        }
        assertThat(Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(Object::toString).toList()).containsExactly("1", "2", "3", "4");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Long.class
        )).isEqualTo(1L);
        assertThat(entityManager.getEntityManagerFactory().getProperties())
                .containsEntry("hibernate.hbm2ddl.auto", "validate");
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
        Todo first = persistTodo(persistUser("반복사용자"), 100L);
        if (deleted) {
            jdbc.update("UPDATE todos SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                    first.getId());
        }
        assertViolation(() -> jdbc.update("""
                INSERT INTO todos (
                    user_id, recurrence_rule_id, recurrence_occurrence_at, title,
                    start_at, due_at, current_schedule_date, all_day,
                    user_estimated_duration_minutes, user_estimate_source,
                    selected_estimate_type, status
                )
                SELECT user_id, recurrence_rule_id, recurrence_occurrence_at, title,
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
                Arguments.of("recurrence_rule_id = 100", "ck_todos_recurrence_pair"),
                Arguments.of("due_at = start_at", "ck_todos_schedule_order"),
                Arguments.of("title = '   '", "ck_todos_title_not_blank"),
                Arguments.of("all_day = true, due_at = due_at - INTERVAL '1 hour'",
                        "ck_todos_all_day_boundaries")
        );
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
