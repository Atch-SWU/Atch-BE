package com.swu.atch.ai.controller;

import com.swu.atch.ai.domain.PredictionReasonCode;
import com.swu.atch.ai.dto.request.*;
import com.swu.atch.ai.dto.response.*;
import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.jwt.JwtTokenService;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

// 공개 DTO·인증·실제 Service 연결과 OpenAPI를 검증한다. 제공자는 기본 비활성이다.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "app.api.contracts.enabled=true"})
@Transactional
class AiApiContractTest extends IsolatedAuthIntegrationTest {
    private static final String DRAFT = "11111111-1111-4111-8111-111111111111";
    private static final String CHILD = "22222222-2222-4222-8222-222222222222";
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired Validator validator;
    @Autowired UserRepository users;
    @Autowired AuthAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountCredentials credentials;
    @Autowired JwtTokenService tokens;
    private String accessToken;

    @BeforeEach
    void localAccount() {
        var user = users.save(new User("인공계약"));
        var account = accounts.save(AuthAccount.local(user, UUID.randomUUID() + "@atch.invalid", encoder.encode("Contract123!")));
        accessToken = tokens.issueAccessToken(user.getId(), credentials.stamp(account));
    }

    static Stream<Arguments> routes() {
        return Stream.of(
                Arguments.of("POST", "/api/v1/ai/drafts", Map.of()),
                Arguments.of("GET", "/api/v1/ai/profile", null),
                Arguments.of("POST", "/api/v1/ai/profile/recalculate", null),
                Arguments.of("POST", "/api/v1/ai/similar-todos", Map.of("title", "기획안 초안 작성")),
                Arguments.of("POST", "/api/v1/ai/time-predictions", prediction()),
                Arguments.of("POST", "/api/v1/ai/time-predictions/batch", batch()),
                Arguments.of("POST", "/api/v1/ai/task-decompositions", decomposition()),
                Arguments.of("POST", "/api/v1/todos/decomposed", confirmation()));
    }

    @ParameterizedTest
    @MethodSource("routes")
    void validRequestsRequireAuthenticationAndReachImplementedServices(String method, String path, Map<String, Object> body) throws Exception {
        var request = request(HttpMethod.valueOf(method), path);
        if (body != null) request.contentType("application/json").content(json.writeValueAsString(body));
        assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).isEqualTo(401);
        var authenticated = request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + accessToken);
        if (body != null) authenticated.contentType("application/json").content(json.writeValueAsString(body));
        var response = mvc.perform(authenticated).andReturn().getResponse();
        int expected = path.equals("/api/v1/ai/drafts") ? 201
                : path.contains("profile") || path.endsWith("similar-todos") ? 200 : 404;
        assertThat(response.getStatus()).isEqualTo(expected);
    }

    static Stream<Arguments> invalidRequests() {
        var missingDraft = prediction();
        missingDraft.remove("draftId");
        var zeroTime = prediction();
        zeroTime.put("userEstimatedDurationMinutes", 0);
        var invalidId = prediction();
        invalidId.put("draftId", "not-a-uuid");
        var emptySteps = batch();
        emptySteps.put("tasks", List.of());
        var tooMany = batch();
        tooMany.put("tasks", java.util.Collections.nCopies(21, task()));
        var invalidStep = batch();
        invalidStep.put("tasks", List.of(Map.of("draftId", CHILD, "title", "메시지 정하기", "userEstimatedDurationMinutes", 0)));
        var nullStep = batch();
        var list = new ArrayList<>();
        list.add(null);
        nullStep.put("tasks", list);
        var invalidChoice = confirmation();
        invalidChoice.put("estimateChoice", Map.of("draftId", DRAFT, "selectedEstimateType", "UNKNOWN"));
        var missingChoiceDraft = confirmation();
        missingChoiceDraft.put("estimateChoice", Map.of("selectedEstimateType", "USER"));
        var missingRequestId = confirmation();
        missingRequestId.remove("creationRequestId");
        return Stream.of(
                Arguments.of("/api/v1/ai/time-predictions", missingDraft),
                Arguments.of("/api/v1/ai/time-predictions", zeroTime),
                Arguments.of("/api/v1/ai/time-predictions", invalidId),
                Arguments.of("/api/v1/ai/time-predictions/batch", emptySteps),
                Arguments.of("/api/v1/ai/time-predictions/batch", tooMany),
                Arguments.of("/api/v1/ai/time-predictions/batch", invalidStep),
                Arguments.of("/api/v1/ai/time-predictions/batch", nullStep),
                Arguments.of("/api/v1/todos/decomposed", invalidChoice),
                Arguments.of("/api/v1/todos/decomposed", missingChoiceDraft),
                Arguments.of("/api/v1/todos/decomposed", missingRequestId),
                Arguments.of("/api/v1/ai/drafts", Map.of("todoId", -1)),
                Arguments.of("/api/v1/ai/similar-todos", Map.of("title", " ")));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void nestedAndScalarValidationRejectsInvalidInputs(String path, Map<String, Object> body) throws Exception {
        assertThat(postBody(path, body).getStatus()).isEqualTo(400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"짧은 내용\n다음 줄", "짧은 내용\r다음 줄", "짧은 내용\u2028다음 줄"})
    void optionalTaskDescriptionRejectsLineBreaks(String description) throws Exception {
        var body = decomposition();
        body.put("taskDescription", description);
        assertThat(postBody("/api/v1/ai/task-decompositions", body).getStatus()).isEqualTo(400);
    }

    @Test
    void taskDescriptionIsOptionalShortTextAndNotAWorkloadOrTimeField() throws Exception {
        var body = decomposition();
        body.put("taskDescription", "발표에서 문제와 해결 방향을 설명할 기획안");
        assertThat(postBody("/api/v1/ai/task-decompositions", body).getStatus()).isEqualTo(404);
        body.put("taskDescription", "가".repeat(201));
        assertThat(postBody("/api/v1/ai/task-decompositions", body).getStatus()).isEqualTo(400);
        body.remove("taskDescription");
        assertThat(postBody("/api/v1/ai/task-decompositions", body).getStatus()).isEqualTo(404);
    }

    @Test
    void malformedAndMissingJsonBodiesAre400() throws Exception {
        for (var content : List.of("", "{invalid-json")) {
            assertThat(mvc.perform(post("/api/v1/ai/time-predictions").header("Authorization", "Bearer " + accessToken)
                    .contentType("application/json").content(content)).andReturn().getResponse().getStatus()).isEqualTo(400);
        }
    }

    @Test
    void proposalTimesRemainSeparateAndDecompositionHasNoAiTimeSeed() throws Exception {
        var now = Instant.parse("2026-10-29T00:00:00Z");
        var profile = new TimeEstimationProfileResponse(TimeEstimationProfileResponse.State.AVAILABLE,
                TimeEstimationProfileResponse.Source.FRESH_COMPUTATION, true, 1.5, 8, 0.2, 0.75, 0.6,
                TimeEstimationProfileResponse.Reliability.MEDIUM, now, now);
        var predictions = List.of(result(15, 20, now), result(10, 20, now),
                result(15, 20, now), result(40, 50, now));
        var response = new TimePredictionBatchResponse(UUID.fromString(DRAFT), predictions, profile,
                80, 110, "example-offer", now.plusSeconds(600), now);
        var tree = json.readTree(json.writeValueAsString(response));
        assertThat(tree.path("predictions").get(0).path("userEstimatedDurationMinutes").asInt()).isEqualTo(15);
        assertThat(tree.path("predictions").get(0).path("aiEstimatedDurationMinutes").asInt()).isEqualTo(20);
        assertThat(tree.path("totalUserEstimatedDurationMinutes").asLong()).isEqualTo(80);
        assertThat(tree.path("totalAiEstimatedDurationMinutes").asLong()).isEqualTo(110);
        assertThat(tree.path("predictions").get(0).path("usage").path("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(tree.path("predictions").get(0).path("asOf").asText()).isEqualTo("2026-10-29T00:00:00Z");
        var step = new TaskDecompositionResponse.Step(UUID.fromString(CHILD), 1, "메시지 정하기");
        var stepJson = json.readTree(json.writeValueAsString(step));
        assertThat(stepJson.propertyNames()).containsExactlyInAnyOrder("draftId", "position", "title");
        assertThat(stepJson.has("aiEstimatedDurationMinutes")).isFalse();
        var failure = json.readTree(json.writeValueAsString(
                new AiUsageResponse(AiUsageResponse.State.RETRYABLE_FAILURE, true, null, false)));
        assertThat(failure.path("retryAllowed").asBoolean()).isTrue();
        assertThat(failure.path("succeededAt").isNull()).isTrue();
    }

    @Test
    void noHistoryAndStoredProfileDoNotInventACalculationTimestamp() throws Exception {
        var response = new TimeEstimationProfileResponse(TimeEstimationProfileResponse.State.NO_HISTORY,
                TimeEstimationProfileResponse.Source.STORED_SNAPSHOT, false, 1.0, 0, null, 0, 0,
                TimeEstimationProfileResponse.Reliability.LOW, null, null);
        var tree = json.readTree(json.writeValueAsString(response));
        assertThat(tree.path("durationCorrectionFactor").asDouble()).isEqualTo(1.0);
        assertThat(tree.path("sampleCount").asInt()).isZero();
        assertThat(tree.path("logRatioStddev").isNull()).isTrue();
        assertThat(tree.path("calculatedAsOf").isNull()).isTrue();
    }

    @Test
    void everyBodyHasUsableOpenApiExamplesAndImplementedContracts() throws Exception {
        var response = mvc.perform(get("/v3/api-docs")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        var api = json.readTree(response.getContentAsString());
        var examples = List.of(
                new Example("/api/v1/ai/drafts", "AiDraftRequest", AiDraftRequest.class),
                new Example("/api/v1/ai/similar-todos", "SimilarTodoSearchRequest", SimilarTodoSearchRequest.class),
                new Example("/api/v1/ai/time-predictions", "TimePredictionRequest", TimePredictionRequest.class),
                new Example("/api/v1/ai/time-predictions/batch", "TimePredictionBatchRequest", TimePredictionBatchRequest.class),
                new Example("/api/v1/ai/task-decompositions", "TaskDecompositionRequest", TaskDecompositionRequest.class),
                new Example("/api/v1/todos/decomposed", "DecomposedTodoCreateRequest", DecomposedTodoCreateRequest.class));
        for (var example : examples) {
            var media = api.path("paths").path(example.path()).path("post").path("requestBody")
                    .path("content").path("application/json");
            assertThat(media.path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/" + example.schema());
            var value = media.path("examples").path("요청 예시").path("value");
            assertThat(value.isObject()).isTrue();
            assertThat(validator.validate(json.treeToValue(value, example.type()))).isEmpty();
        }
        for (var arguments : routes().toList()) {
            var args = arguments.get();
            var operation = api.path("paths").path((String) args[1]).path(((String) args[0]).toLowerCase());
            assertThat(operation.path("security").toString()).contains("bearerAuth");
            assertThat(operation.path("description").asText()).doesNotContain("현재 501");
            assertThat(operation.path("responses").has("501")).isFalse();
            assertThat(operation.path("parameters").toString()).doesNotContain("\"name\":\"jwt\"");
        }
        var schemas = api.path("components").path("schemas");
        var decomposition = schemas.path("TaskDecompositionRequest").path("properties");
        assertThat(decomposition.has("taskDescription")).isTrue();
        assertThat(decomposition.has("workload")).isFalse();
        assertThat(decomposition.has("userEstimatedDurationMinutes")).isFalse();
        assertThat(schemas.path("TimePredictionRequest").path("required").toString())
                .contains("draftId", "title", "userEstimatedDurationMinutes");
        var oneTime = api.path("paths").path("/api/v1/ai/time-predictions").path("post");
        assertThat(oneTime.path("description").asText()).contains("성공", "1회", "Baseline");
        assertThat(oneTime.path("responses").has("409")).isTrue();
        assertThat(api.path("paths").path("/api/v1/todos").path("post").path("responses").has("501")).isFalse();
        var evidence = Path.of("build/verification/ai-20261029/openapi.json");
        Files.createDirectories(evidence.getParent());
        Files.writeString(evidence, response.getContentAsString());
    }

    @Test
    void voyagePropertiesReferenceEnvironmentOnlyAndDefaultToDisabled() throws Exception {
        var properties = new Properties();
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(input);
        }
        assertThat(properties.getProperty("app.ai.voyage.api-key")).isEqualTo("$" + "{VOYAGE_API_KEY:}");
        assertThat(properties.getProperty("app.ai.voyage.enabled")).isEqualTo("$" + "{VOYAGE_ENABLED:false}");
    }

    private record Example(String path, String schema, Class<?> type) { }

    private TimePredictionResult result(int userMinutes, int aiMinutes, Instant at) {
        return new TimePredictionResult(UUID.randomUUID(), null, userMinutes, 1.5, userMinutes * 1.5,
                aiMinutes, aiMinutes / (userMinutes * 1.5), TimePredictionResult.Mode.HYBRID,
                TimePredictionResult.ClaudeStatus.APPLIED, SimilarTodoSearchResponse.VoyageStatus.AVAILABLE,
                List.of(PredictionReasonCode.BASELINE_RETAINED), List.of(),
                new AiUsageResponse(AiUsageResponse.State.SUCCEEDED, false, at, true), true, at, null);
    }

    private org.springframework.mock.web.MockHttpServletResponse postBody(String path, Map<String, Object> body) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + accessToken)
                .contentType("application/json").content(json.writeValueAsString(body))).andReturn().getResponse();
    }

    private static Map<String, Object> task() {
        return Map.of("draftId", CHILD, "title", "메시지 정하기", "userEstimatedDurationMinutes", 10);
    }

    private static Map<String, Object> prediction() {
        return new LinkedHashMap<>(Map.of("draftId", DRAFT, "title", "기획안 초안 작성", "userEstimatedDurationMinutes", 30));
    }

    private static Map<String, Object> batch() {
        return new LinkedHashMap<>(Map.of("decompositionDraftId", DRAFT, "sourceTaskName", "기획안 초안 작성",
                "tasks", List.of(task())));
    }

    private static Map<String, Object> decomposition() {
        return new LinkedHashMap<>(Map.of("draftId", DRAFT, "title", "기획안 초안 작성",
                "startDate", "2026-10-29", "startTime", "18:00:00",
                "dueDate", "2026-10-29", "dueTime", "20:00:00", "allDay", false));
    }

    private static Map<String, Object> confirmation() {
        var body = decomposition();
        body.remove("draftId");
        body.remove("title");
        body.put("creationRequestId", "33333333-3333-4333-8333-333333333333");
        body.put("decompositionDraftId", DRAFT);
        body.put("sourceTaskName", "기획안 초안 작성");
        body.put("tasks", List.of(task()));
        return body;
    }
}

