package com.swu.atch.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

// JPA 초기화 전에 데이터를 커밋하고 V3→V9→V10 보존·중단·재실행을 확인
@Testcontainers
class FlywayMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("atch_migration_test")
            .withLabel("atch.verification", "v10")
            .withReuse(false);

    @Test
    void preservesV3AndV9DataWhenUpgradingToV10AndDoesNotReapplyMigrations() throws Exception {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(POSTGRES.getJdbcUrl());
            assertThat(connection.getCatalog()).isEqualTo("atch_migration_test");
            assertThat(connection.getAutoCommit()).isTrue();
        }
        var jdbc = new JdbcTemplate(dataSource);
        Flyway v3 = migration(dataSource, "3");
        assertThat(v3.migrate().migrationsExecuted).isEqualTo(3);
        v3.validate();

        long owner = jdbc.queryForObject("""
                INSERT INTO users (nickname, bio, onboarding_completed_at)
                VALUES ('기존사용자', '보존할 자기소개', TIMESTAMPTZ '2026-10-20 12:00:00+09')
                RETURNING id
                """, Long.class);
        long other = jdbc.queryForObject(
                "INSERT INTO users (nickname) VALUES ('기본설정') RETURNING id", Long.class);
        // 합성 값이며 실제 계정·인증 토큰을 사용하지 않음
        jdbc.update("""
                INSERT INTO auth_accounts (user_id, provider, email, password_hash, last_login_at)
                VALUES (?, 'LOCAL', 'preserve@atch.invalid', ?, TIMESTAMPTZ '2026-10-21 15:00:00Z')
                """, owner, UUID.randomUUID().toString());
        jdbc.update("""
                INSERT INTO auth_accounts (user_id, provider, provider_user_id)
                VALUES (?, 'GOOGLE', 'synthetic-social-identity')
                """, owner);
        jdbc.update("""
                INSERT INTO user_settings (user_id, task_switch_restriction_enabled,
                    task_switch_wait_minutes, repeat_recommendation_enabled)
                VALUES (?, false, 17, false)
                """, owner);
        jdbc.update("INSERT INTO user_settings (user_id) VALUES (?)", other);
        assertDefaults(jdbc, other);

        var before = dataSnapshot(jdbc);
        var historyV3 = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");

        Flyway v9 = migration(dataSource, "9");
        assertThat(v9.getConfiguration().getDataSource()).isSameAs(dataSource);
        assertThat(v9.migrate().migrationsExecuted).isEqualTo(6);
        v9.validate();
        assertThat(v9.info().current().getVersion().toString()).isEqualTo("9");
        assertThat(v9.info().pending()).isEmpty();
        assertTrue(before.equals(dataSnapshot(jdbc)), "V3 사용자·인증·설정 데이터가 보존되어야 합니다.");
        assertTrue(historyV3.equals(jdbc.queryForList("""
                SELECT * FROM flyway_schema_history
                WHERE version IN ('1', '2', '3') ORDER BY installed_rank
                """)), "기존 적용 이력이 보존되어야 합니다.");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM flyway_schema_history
                WHERE success AND checksum IS NOT NULL
                """, Long.class)).isEqualTo(9);

        // 기존 identity와 기본값이 확장 이후 신규 데이터에도 동작
        long next = jdbc.queryForObject(
                "INSERT INTO users (nickname) VALUES ('확장후사용자') RETURNING id", Long.class);
        assertThat(next).isGreaterThan(other);
        jdbc.update("INSERT INTO user_settings (user_id) VALUES (?)", next);
        assertDefaults(jdbc, next);

        var afterUpgrade = dataSnapshot(jdbc);
        var historyV9 = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        Flyway rerun = migration(dataSource, "9");
        rerun.validate();
        assertThat(rerun.migrate().migrationsExecuted).isZero();
        assertTrue(afterUpgrade.equals(dataSnapshot(jdbc)), "재실행 시 기존 데이터가 바뀌면 안 됩니다.");
        assertTrue(historyV9.equals(jdbc.queryForList(
                "SELECT * FROM flyway_schema_history ORDER BY installed_rank")),
                "재실행 시 체크섬·순서·적용 시각을 포함한 이력이 바뀌면 안 됩니다.");
        long todoId = jdbc.queryForObject("""
                INSERT INTO todos (user_id, title, memo, start_at, due_at, current_schedule_date, all_day,
                    user_estimated_duration_minutes, user_estimate_source, selected_estimate_type, status)
                VALUES (?, '보존할 일반 작업', 'V9 기존 메모', TIMESTAMPTZ '2026-10-21 00:00:00+09',
                    TIMESTAMPTZ '2026-10-21 01:00:00+09', DATE '2026-10-21', false, 30, 'USER_INPUT', 'USER', 'PENDING')
                RETURNING id
                """, Long.class, owner);
        var todoBefore = jdbc.queryForMap("SELECT * FROM todos WHERE id=?", todoId);

        // 이 폐기 가능한 DB에만 의도적으로 만든 이전 버전의 반복 참조
        long legacyTodoId = jdbc.queryForObject("""
                INSERT INTO todos (user_id, recurrence_rule_id, recurrence_occurrence_at, title,
                    start_at, due_at, current_schedule_date, all_day, user_estimated_duration_minutes,
                    user_estimate_source, selected_estimate_type, status)
                SELECT user_id, 123456, start_at, '중단 검증 전용 합성 행', start_at, due_at,
                    current_schedule_date, all_day, user_estimated_duration_minutes,
                    user_estimate_source, selected_estimate_type, status FROM todos WHERE id=?
                RETURNING id
                """, Long.class, todoId);
        Flyway guarded = migration(dataSource, "10");
        var failure = assertThrows(FlywayException.class, guarded::migrate);
        assertThat(failure.getMessage()).contains("existing recurrence references require review");
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.repeat_patterns')", String.class)).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='todos' AND column_name='recurrence_period_start_date'
                """, Long.class)).isZero();
        assertTrue(historyV9.equals(jdbc.queryForList(
                "SELECT * FROM flyway_schema_history ORDER BY installed_rank")),
                "중단된 V10은 기존 적용 이력을 바꾸면 안 됩니다.");
        assertThat(jdbc.update("DELETE FROM todos WHERE id=? AND recurrence_rule_id=123456", legacyTodoId))
                .isEqualTo(1);

        Flyway v10 = migration(dataSource, "10");
        assertThat(v10.getConfiguration().getDataSource()).isSameAs(dataSource);
        assertThat(v10.migrate().migrationsExecuted).isEqualTo(1);
        v10.validate();
        assertThat(v10.info().current().getVersion().toString()).isEqualTo("10");
        assertThat(v10.info().pending()).isEmpty();
        assertThat(jdbc.queryForList("""
                SELECT version FROM flyway_schema_history ORDER BY installed_rank
                """, String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM flyway_schema_history WHERE success AND checksum IS NOT NULL
                """, Long.class)).isEqualTo(10);
        assertTrue(historyV9.equals(jdbc.queryForList("""
                SELECT * FROM flyway_schema_history WHERE version <> '10' ORDER BY installed_rank
                """)), "V10 이후에도 V1~V9 이력이 그대로 유지되어야 합니다.");
        assertTrue(afterUpgrade.equals(dataSnapshot(jdbc)), "V10 이후에도 사용자·인증·설정 데이터가 보존되어야 합니다.");
        var todoAfter = jdbc.queryForMap("SELECT * FROM todos WHERE id=?", todoId);
        assertThat(todoAfter).containsKey("recurrence_period_start_date");
        assertThat(todoAfter.remove("recurrence_period_start_date")).isNull();
        assertThat(todoAfter).isEqualTo(todoBefore);

        var historyV10 = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        Flyway rerunV10 = migration(dataSource, "10");
        rerunV10.validate();
        assertThat(rerunV10.migrate().migrationsExecuted).isZero();
        assertTrue(historyV10.equals(jdbc.queryForList(
                "SELECT * FROM flyway_schema_history ORDER BY installed_rank")),
                "V10 재실행 시 체크섬·순서·적용 시각을 포함한 이력이 바뀌면 안 됩니다.");
        assertTrue(afterUpgrade.equals(dataSnapshot(jdbc)), "V10 재실행 시 기존 데이터가 바뀌면 안 됩니다.");
        assertThat(jdbc.queryForMap("SELECT * FROM todos WHERE id=?", todoId))
                .containsAllEntriesOf(todoBefore).containsEntry("recurrence_period_start_date", null);
        System.out.println("V3->V9->V10: preserved users/auth/settings/non-recurring todo; "
                + "legacy recurrence guard verified; rerun migrations=0; "
                + "database=atch_migration_test; container=" + POSTGRES.getContainerId());
    }

    private Flyway migration(DriverManagerDataSource dataSource, String target) {
        return Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration").target(target)
                .cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false)
                .validateOnMigrate(true).load();
    }

    private Map<String, List<Map<String, Object>>> dataSnapshot(JdbcTemplate jdbc) {
        return Map.of(
                "users", jdbc.queryForList("SELECT * FROM users ORDER BY id"),
                "auth", jdbc.queryForList("SELECT * FROM auth_accounts ORDER BY id"),
                "settings", jdbc.queryForList("SELECT * FROM user_settings ORDER BY user_id"));
    }

    private void assertDefaults(JdbcTemplate jdbc, long userId) {
        assertThat(jdbc.queryForMap("""
                SELECT task_switch_restriction_enabled, task_switch_wait_minutes,
                    repeat_recommendation_enabled FROM user_settings WHERE user_id = ?
                """, userId)).containsEntry("task_switch_restriction_enabled", true)
                .containsEntry("task_switch_wait_minutes", 10)
                .containsEntry("repeat_recommendation_enabled", true);
    }
}
