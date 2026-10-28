package com.swu.atch.support;

import com.swu.atch.AtchApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

// 기존 AUTH·컨텍스트 테스트에만 적용하는 격리 기반
@SpringBootTest(classes = AtchApplication.class, properties = {
        "spring.config.location=classpath:/application-auth-test.properties",
        "spring.config.import=",
        "spring.profiles.active=auth-test",
        "app.ai.claude.enabled=false"
})
@ActiveProfiles("auth-test")
@ContextConfiguration(initializers = IsolatedAuthIntegrationTest.SafetyInitializer.class)
@Import(IsolatedAuthIntegrationTest.Containers.class)
public abstract class IsolatedAuthIntegrationTest {

    @TestBean(name = "jwtKeyPair", methodName = "testKeyPair", enforceOverride = true)
    private KeyPair jwtKeyPair;

    // 실제 SMTP 전송만 대체하고 JWT·DB·Redis 로직은 그대로 사용
    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired private PostgreSQLContainer postgres;
    @Autowired private GenericContainer<?> redis;
    @Autowired private DataSource dataSource;
    @Autowired private Flyway flyway;
    @Autowired private LettuceConnectionFactory redisConnectionFactory;
    @Autowired private ApplicationContext context;

    static KeyPair testKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @DynamicPropertySource
    static void testSecrets(DynamicPropertyRegistry registry) {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String hmac = Base64.getEncoder().encodeToString(random);
        registry.add("app.auth.email-code-hmac-secret", () -> hmac);
    }

    @BeforeEach
    void verifiesIsolatedConnections() throws Exception {
        assertThat(flyway.getConfiguration().getDataSource()).isSameAs(dataSource);
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(postgres.getJdbcUrl());
            assertThat(connection.getCatalog()).isEqualTo("atch_auth_test");
        }
        assertThat(redisConnectionFactory.getHostName()).isEqualTo(redis.getHost());
        assertThat(redisConnectionFactory.getPort()).isEqualTo(redis.getMappedPort(6379));
        assertThat(redisConnectionFactory.getDatabase()).isZero();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("11");
        assertThat(context.containsBean("anthropicClient")).isFalse();
        assertThat(context.containsBean("claudeApiClient")).isFalse();
    }

    @AfterEach
    void verifiesNoEmailWasSent() {
        verifyNoInteractions(mailSender);
    }

    public static class SafetyInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            var env = context.getEnvironment();
            if (!"classpath:/application-auth-test.properties".equals(
                    env.getProperty("spring.config.location"))
                    || env.getProperty("app.ai.claude.enabled", Boolean.class, true)) {
                throw new IllegalStateException("AUTH 테스트 전용 설정이 아닙니다.");
            }
            for (String profile : env.getActiveProfiles()) {
                if (!profile.equals("auth-test")) {
                    throw new IllegalStateException("AUTH 테스트 외부 profile을 허용하지 않습니다.");
                }
            }
            for (var source : env.getPropertySources()) {
                if (source.getName().contains(".env")) {
                    throw new IllegalStateException("AUTH 테스트에 .env가 로드됐습니다.");
                }
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Containers {
        // 컨텍스트가 종료될 때 연결 자원 이후에 컨테이너도 종료
        @Bean(destroyMethod = "stop")
        PostgreSQLContainer postgres() {
            var container = new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("atch_auth_test")
                    .withLabel("atch.verification", "account-api")
                    .withReuse(false);
            try {
                container.start();
                return container;
            } catch (RuntimeException ex) {
                container.stop();
                throw ex;
            }
        }

        @Bean(destroyMethod = "stop")
        GenericContainer<?> redis() {
            var container = new GenericContainer<>(DockerImageName.parse("redis:7.2"))
                    .withExposedPorts(6379)
                    .withCommand("redis-server", "--save", "", "--appendonly", "no")
                    .withLabel("atch.verification", "account-api")
                    .withReuse(false);
            try {
                container.start();
                return container;
            } catch (RuntimeException ex) {
                container.stop();
                throw ex;
            }
        }

        @Bean
        @FlywayDataSource
        DataSource dataSource(PostgreSQLContainer postgres) {
            return new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        }

        @Bean
        FlywayMigrationStrategy isolatedMigration(DataSource dataSource) {
            return flyway -> {
                if (flyway.getConfiguration().getDataSource() != dataSource
                        || !flyway.getConfiguration().isCleanDisabled()) {
                    throw new IllegalStateException("AUTH Flyway DataSource 격리 오류입니다.");
                }
                flyway.migrate();
                flyway.validate();
            };
        }

        @Bean
        LettuceConnectionFactory redisConnectionFactory(GenericContainer<?> redis) {
            return new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                    redis.getHost(), redis.getMappedPort(6379)));
        }
    }
}
