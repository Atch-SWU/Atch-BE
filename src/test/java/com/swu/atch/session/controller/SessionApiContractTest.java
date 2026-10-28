package com.swu.atch.session.controller;

import com.swu.atch.ai.controller.*;
import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.jwt.JwtTokenService;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.report.controller.ReportController;
import com.swu.atch.session.domain.SessionEndReason;
import com.swu.atch.session.dto.request.SessionPageQuery;
import com.swu.atch.session.dto.response.SessionResponse;
import com.swu.atch.session.dto.response.TimerStateResponse;
import com.swu.atch.setting.controller.TimerSettingController;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

// 실제 Controller의 인증·DTO·OpenAPI 계약을 유지한다. 상태 전환은 별도 통합 테스트에서 검증한다.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "app.api.contracts.enabled=false"})
@Transactional
class SessionApiContractTest extends IsolatedAuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    @Autowired AuthAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountCredentials credentials;
    @Autowired JwtTokenService tokens;
    @Autowired ApplicationContext context;
    @Autowired DataSource dataSource;
    @Autowired LettuceConnectionFactory redis;

    private String accessToken;

    @BeforeEach
    void localAccount() {
        var user = users.save(new User("세션계약"));
        var account = accounts.save(AuthAccount.local(user, UUID.randomUUID() + "@atch.invalid", encoder.encode("Contract123!")));
        accessToken = tokens.issueAccessToken(user.getId(), credentials.stamp(account));
    }

    static Stream<Arguments> routes() {
        return Stream.of(
                Arguments.of("POST", "/api/v1/todos/1/timer/start", "{\"expectedLastSessionId\":null}"),
                Arguments.of("POST", "/api/v1/todos/1/timer/pause", "{\"sessionId\":1}"),
                Arguments.of("POST", "/api/v1/todos/1/timer/resume", "{\"lastSessionId\":1}"),
                Arguments.of("GET", "/api/v1/timer/current", null),
                Arguments.of("POST", "/api/v1/timer/switches", "{\"sourceTodoId\":1,\"targetTodoId\":2,\"sourceSessionId\":1}"),
                Arguments.of("POST", "/api/v1/timer/switches/11111111-1111-4111-8111-111111111111/confirm", null),
                Arguments.of("DELETE", "/api/v1/timer/switches/11111111-1111-4111-8111-111111111111", null),
                Arguments.of("GET", "/api/v1/todos/1/sessions", null),
                Arguments.of("GET", "/api/v1/todos/1/completion-summary", null),
                Arguments.of("GET", "/api/v1/settings/timer", null),
                Arguments.of("PATCH", "/api/v1/settings/timer", "{\"taskSwitchRestrictionEnabled\":true,\"taskSwitchWaitMinutes\":10}")
        );
    }

    @ParameterizedTest
    @MethodSource("routes")
    void routesRequireRealAuthenticationAndReachImplementedServices(String method, String path, String body) throws Exception {
        var unauthenticated = request(HttpMethod.valueOf(method), path);
        if (body != null) unauthenticated.contentType("application/json").content(body);
        assertThat(mvc.perform(unauthenticated).andReturn().getResponse().getStatus()).isEqualTo(401);
        var authenticated = request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + accessToken);
        if (body != null) authenticated.contentType("application/json").content(body);
        var response = mvc.perform(authenticated).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(path.equals("/api/v1/timer/current") || path.equals("/api/v1/settings/timer") ? 200 : 404);
    }

    static Stream<Arguments> invalidCommands() {
        return Stream.of(
                Arguments.of("/api/v1/todos/1/timer/start", "{\"expectedLastSessionId\":0}"),
                Arguments.of("/api/v1/todos/1/timer/pause", "{}"),
                Arguments.of("/api/v1/todos/1/timer/resume", "{\"lastSessionId\":-1}"),
                Arguments.of("/api/v1/timer/switches", "{\"sourceTodoId\":1,\"targetTodoId\":2}"),
                Arguments.of("/api/v1/todos/0/timer/start", "{}")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCommands")
    void invalidCommandFieldsAre400BeforeStub(String path, String body) throws Exception {
        assertThat(mvc.perform(post(path).header("Authorization", "Bearer " + accessToken)
                .contentType("application/json").content(body)).andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void patchRejectsEmptyOrZeroAndAllowsSingleFieldUpdate() throws Exception {
        for (var body : List.of("{}", "{\"taskSwitchWaitMinutes\":0}")) {
            assertThat(mvc.perform(patch("/api/v1/settings/timer").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(body)).andReturn().getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(mvc.perform(patch("/api/v1/settings/timer").header("Authorization", "Bearer " + accessToken)
                .contentType("application/json").content("{\"taskSwitchRestrictionEnabled\":false}"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void invalidPageAndUuidAre400() throws Exception {
        for (var params : List.of(Map.of("page", "-1"), Map.of("size", "0"), Map.of("size", "101"))) {
            var request = get("/api/v1/todos/1/sessions").header("Authorization", "Bearer " + accessToken);
            params.forEach(request::param);
            assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(mvc.perform(post("/api/v1/timer/switches/not-a-uuid/confirm")
                .header("Authorization", "Bearer " + accessToken)).andReturn().getResponse().getStatus()).isEqualTo(400);
        var query = new SessionPageQuery(null, null);
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
    }

    @Test
    void sessionAndIdleTimerPreserveNullableEndFieldsAndUtcSeconds() throws Exception {
        var start = Instant.parse("2026-10-28T14:50:00Z");
        var open = json.readTree(json.writeValueAsString(new SessionResponse(1L, 2L, start, null, null, null)));
        assertThat(open.path("endedAt").isNull()).isTrue();
        assertThat(open.path("durationSeconds").isNull()).isTrue();
        assertThat(open.path("endReason").isNull()).isTrue();
        var closed = json.readTree(json.writeValueAsString(new SessionResponse(1L, 2L, start, start, 0, SessionEndReason.PAUSED)));
        assertThat(closed.path("durationSeconds").asInt()).isZero();
        assertThat(closed.path("endReason").asText()).isEqualTo("PAUSED");
        var idle = json.readTree(json.writeValueAsString(new TimerStateResponse(TimerStateResponse.State.IDLE,
                null, null, null, null, null, null, 0, 0, 0, 0, 0, null, start)));
        assertThat(idle.path("state").asText()).isEqualTo("IDLE");
        assertThat(idle.path("sessionId").isNull()).isTrue();
        assertThat(idle.path("serverTime").asText()).isEqualTo("2026-10-28T14:50:00Z");
    }

    @Autowired org.springframework.context.ApplicationContext applicationContext;
    @Test
    void implementedAiControllersAreAvailableWithoutContractOptIn() {
        for (var type : new Class<?>[]{TimeEstimationProfileController.class, SimilarTodoController.class,
                TimePredictionController.class, TaskDecompositionController.class}) {
            assertThat(applicationContext.getBean(type)).isNotNull();
        }
    }

    @Test
    void openApiDocumentsTimerCommandsAndKeepsTheExistingCompletionContract() throws Exception {
        var api = json.readTree(mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString());
        for (var route : routes().toList()) {
            var args = route.get();
            String path = ((String) args[1]).replace("/todos/1/", "/todos/{todoId}/")
                    .replace("11111111-1111-4111-8111-111111111111", "{switchId}");
            var operation = api.path("paths").path(path).path(((String) args[0]).toLowerCase());
            assertThat(operation.path("description").asText()).doesNotContain("현재 501");
            assertThat(operation.path("security").toString()).contains("bearerAuth");
            assertThat(operation.path("responses").has("501")).isFalse();
            if (args[2] != null) {
                var media = operation.path("requestBody").path("content").path("application/json");
                assertThat(media.path("schema").path("$ref").asText()).startsWith("#/components/schemas/");
                assertThat(media.path("examples").path("요청 예시").path("value").isObject()).isTrue();
            }
        }
        var complete = api.path("paths").path("/api/v1/todos/{todoId}/complete").path("post");
        assertThat(complete.path("responses").has("501")).isFalse();
        assertThat(complete.path("responses").path("200").path("content").path("application/json")
                .path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/TodoResponse");
    }

    @Test
    void recordsSafeConnectionEvidenceAndDoesNotRegisterExternalClients() throws Exception {
        try (var connection = dataSource.getConnection()) {
            var text = "jdbc=" + connection.getMetaData().getURL() + "\ndatabase=" + connection.getCatalog()
                    + "\nredis=" + redis.getHostName() + ":" + redis.getPort()
                    + "\nconfig=classpath:/application-auth-test.properties\nrsa=in-memory test override\n";
            var path = Path.of("build/verification/core-api-contract-runtime.txt");
            Files.createDirectories(path.getParent());
            Files.writeString(path, text);
        }
        assertThat(context.containsBean("anthropicClient")).isFalse();
        assertThat(context.containsBean("claudeApiClient")).isFalse();
        assertThat(context.containsBean("voyageClient")).isFalse();
    }
}

