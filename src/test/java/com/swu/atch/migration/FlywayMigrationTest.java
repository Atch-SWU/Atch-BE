package com.swu.atch.migration;

import org.flywaydb.core.Flyway;
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

// JPA 초기화 전에 V3 데이터를 커밋하고 원본 V4~V9의 확장 영향을 확인
@Testcontainers
class FlywayMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("atch_migration_test")
            .withLabel("atch.verification", "v9")
            .withReuse(false);

    @Test
    void preservesV3DataWhenUpgradingToV9AndDoesNotReapplyMigrations() throws Exception {
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
        System.out.println("V3->V9: preserved users/auth/settings; rerun migrations=0; "
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
