package com.swu.atch.todo.controller;

import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.jwt.JwtTokenService;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import com.swu.atch.todo.domain.SelectedEstimateType;
import com.swu.atch.todo.domain.TodoStatus;
import com.swu.atch.todo.domain.UserEstimateSource;
import com.swu.atch.todo.dto.request.TodoListQuery;
import com.swu.atch.todo.dto.response.TodoCalendarResponse;
import com.swu.atch.todo.dto.response.TodoPageResponse;
import com.swu.atch.todo.dto.response.TodoResponse;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

// 실제 인증·MVC 바인딩·OpenAPI 계약 검증. 전체 CRUD·소유권 회귀는 별도 통합 테스트에서 확인한다.
// 전용 PostgreSQL/Redis·메모리 RSA·가짜 메일을 재사용하고 각 테스트의 DB 픽스처는 롤백한다.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
@Transactional
class TodoApiContractTest extends IsolatedAuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired Validator validator;
    @Autowired UserRepository users;
    @Autowired AuthAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountCredentials credentials;
    @Autowired JwtTokenService jwtTokens;

    private String accessToken;

    @BeforeEach
    void signedInLocalAccount() {
        var user = users.save(new User("계약검증"));
        var account = accounts.save(AuthAccount.local(user, UUID.randomUUID() + "@atch.invalid", encoder.encode("Contract123!")));
        // 비밀번호 상태 값이 없는 구형 토큰을 쓰면 실제 인증 필터에서 거부된다.
        accessToken = jwtTokens.issueAccessToken(user.getId(), credentials.stamp(account));
    }

    static Stream<String> routes() {
        return Stream.of("POST /api/v1/todos", "GET /api/v1/todos", "GET /api/v1/todos/calendar",
                "GET /api/v1/todos/1", "PUT /api/v1/todos/1", "DELETE /api/v1/todos/1",
                "POST /api/v1/todos/1/complete", "POST /api/v1/todos/1/reopen");
    }

    @ParameterizedTest
    @MethodSource("routes")
    void everyRouteRequiresAuthentication(String route) throws Exception {
        assertThat(perform(routeRequest(route), null).getStatus()).isEqualTo(401);
    }

    @ParameterizedTest
    @CsvSource({"title,blank", "title,long", "memo,long", "startDate,null", "dueDate,null",
            "allDay,null", "userEstimatedDurationMinutes,null", "userEstimatedDurationMinutes,zero"})
    void createAndUpdateShareRequiredAndScalarValidation(String field, String invalid) throws Exception {
        var body = timedInput();
        Object value = switch (invalid) {
            case "blank" -> " ";
            case "long" -> "x".repeat(field.equals("title") ? 201 : 2001);
            case "zero" -> 0;
            default -> null;
        };
        body.put(field, value);
        for (var request : List.of(post("/api/v1/todos"), put("/api/v1/todos/1"))) {
            var response = perform(authenticated(request), body);
            assertThat(response.getStatus()).isEqualTo(400);
            var error = json.readTree(response.getContentAsString());
            assertThat(error.path("code").asText()).isEqualTo("VALIDATION_FAILED");
            assertThat(error.path("errors").toString()).contains("\"field\":\"" + field + "\"");
        }
    }

    @Test
    void allDayInputAllowsNullTimesAndNullableMemoOnCreateAndUpdate() throws Exception {
        var body = timedInput();
        body.put("allDay", true);
        body.put("startTime", null);
        body.put("dueTime", null);
        body.put("memo", null);
        var created = perform(authenticated(post("/api/v1/todos")), body);
        assertThat(created.getStatus()).isEqualTo(201);
        long id = json.readTree(created.getContentAsString()).path("id").asLong();
        assertThat(perform(authenticated(put("/api/v1/todos/" + id)), body).getStatus()).isEqualTo(200);
    }

    @Test
    void missingOrMalformedBodyIsRejectedBeforeService() throws Exception {
        assertThat(perform(authenticated(post("/api/v1/todos").contentType("application/json")), null).getStatus()).isEqualTo(400);
        var response = mvc.perform(authenticated(put("/api/v1/todos/1"))
                .contentType("application/json").content("{invalid-json")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "size,0", "size,101", "status,UNKNOWN", "date,invalid", "page,invalid"})
    void listRejectsInvalidPageSizeEnumAndDate(String field, String value) throws Exception {
        assertThat(perform(authenticated(get("/api/v1/todos").param(field, value)), null).getStatus()).isEqualTo(400);
    }

    @Test
    void listBindsMultipleStatusesAndPagingAndUsesDefaultsWhenOmitted() throws Exception {
        assertThat(perform(authenticated(get("/api/v1/todos").param("from", "2026-10-26").param("to", "2026-11-01")
                .param("status", "PENDING,IN_PROGRESS,PAUSED").param("page", "1").param("size", "20")), null).getStatus()).isEqualTo(200);
        var defaults = new TodoListQuery(null, null, null, null, null, null);
        assertThat(defaults.page()).isZero();
        assertThat(defaults.size()).isEqualTo(20);
        assertThat(defaults.status()).isEmpty();
        assertThat(validator.validate(defaults)).isEmpty();
    }

    @Test
    void calendarRequiresBothDatesAndAcceptsOnlyIsoDateValues() throws Exception {
        for (var request : List.of(get("/api/v1/todos/calendar"),
                get("/api/v1/todos/calendar").param("from", "2026-10-01"),
                get("/api/v1/todos/calendar").param("from", "invalid").param("to", "2026-10-31"))) {
            assertThat(perform(authenticated(request), null).getStatus()).isEqualTo(400);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid"})
    void detailRejectsInvalidTodoId(String id) throws Exception {
        assertThat(perform(authenticated(get("/api/v1/todos/" + id)), null).getStatus()).isEqualTo(400);
    }

    @Test
    void invalidBearerIsRejectedWithoutEnteringTodoContract() throws Exception {
        assertThat(perform(get("/api/v1/todos").header("Authorization", "Bearer invalid"), null).getStatus()).isEqualTo(401);
    }

    @Test
    void responseDtosSerializeUtcDatesEnumsNullsAndPageMetadata() throws Exception {
        var date = LocalDate.of(2026, 10, 29);
        var start = Instant.parse("2026-10-29T00:00:00Z");
        var todo = new TodoResponse(1L, "계약 확인", null, null, start, start.plusSeconds(3600), date, false,
                TodoStatus.PENDING, null, 30, UserEstimateSource.USER_INPUT, null, SelectedEstimateType.USER, 30,
                0L, false, false, null, null, null, start, start);
        var page = json.readTree(json.writeValueAsString(new TodoPageResponse(date, date, List.of(todo), 0, 20, 1L, 1, false)));
        var item = page.path("items").get(0);
        assertThat(item.path("startAt").asText()).isEqualTo("2026-10-29T00:00:00Z");
        assertThat(item.path("currentScheduleDate").asText()).isEqualTo("2026-10-29");
        assertThat(item.path("status").asText()).isEqualTo("PENDING");
        assertThat(item.path("userEstimateSource").asText()).isEqualTo("USER_INPUT");
        assertThat(item.path("selectedEstimateType").asText()).isEqualTo("USER");
        assertThat(item.path("aiEstimatedDurationMinutes").isNull()).isTrue();
        assertThat(item.path("actualDurationSeconds").asLong()).isZero();
        assertThat(page.path("totalElements").asLong()).isEqualTo(1L);
        assertThat(page.path("hasNext").asBoolean()).isFalse();
        var calendar = json.readTree(json.writeValueAsString(new TodoCalendarResponse(date, date, List.of(date))));
        assertThat(calendar.path("dates").get(0).asText()).isEqualTo("2026-10-29");
    }

    @Test
    void openApiPublishesConcreteExamplesSchemasQueriesAndFinalResponses() throws Exception {
        var response = perform(get("/v3/api-docs"), null);
        assertThat(response.getStatus()).isEqualTo(200);
        // 공개 명세는 assertion 실패 시에도 원인 확인이 가능하도록 먼저 보존한다.
        var evidence = Path.of("build/verification/todo-api-openapi.json");
        Files.createDirectories(evidence.getParent());
        Files.writeString(evidence, response.getContentAsString());
        var api = json.readTree(response.getContentAsString());
        var schemas = api.path("components").path("schemas");
        for (var entry : Map.of("post", "TodoCreateRequest", "put", "TodoUpdateRequest").entrySet()) {
            String path = entry.getKey().equals("post") ? "/api/v1/todos" : "/api/v1/todos/{todoId}";
            var body = api.path("paths").path(path).path(entry.getKey()).path("requestBody");
            assertThat(body.path("required").asBoolean()).isTrue();
            var media = body.path("content").path("application/json");
            assertThat(media.path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/" + entry.getValue());
            var properties = schemas.path(entry.getValue()).path("properties");
            var expectedFields = new java.util.ArrayList<>(List.of("title", "memo", "startDate", "startTime", "dueDate", "dueTime", "allDay", "userEstimatedDurationMinutes", "estimateChoice"));
            if (entry.getValue().equals("TodoCreateRequest")) expectedFields.add("creationRequestId");
            assertThat(names(properties)).containsExactlyInAnyOrderElementsOf(expectedFields);
            assertThat(properties.path("userId").isMissingNode()).isTrue();
            assertThat(schemas.path(entry.getValue()).path("required").toString())
                    .contains("title", "startDate", "dueDate", "allDay", "userEstimatedDurationMinutes");
            var timed = media.path("examples").path("시각 지정").path("value");
            var allDay = media.path("examples").path("하루종일").path("value");
            assertThat(timed.isObject()).isTrue();
            assertThat(timed.path("title").asText()).isNotBlank();
            assertThat(allDay.path("allDay").asBoolean()).isTrue();
            assertThat(allDay.path("startTime").isNull()).isTrue();
        }
        for (String route : routes().toList()) {
            String[] parts = route.split(" ", 2);
            String path = parts[1].replace("/1", "/{todoId}");
            var operation = api.path("paths").path(path).path(parts[0].toLowerCase());
            assertThat(operation.path("security").toString()).contains("bearerAuth");
            assertThat(operation.path("description").asText()).doesNotContain("현재 501", "Service 미연결");
            assertThat(names(operation.path("responses"))).contains("400", "401", "503").doesNotContain("501");
            assertThat(operation.path("parameters").toString()).doesNotContain("\"name\":\"jwt\"");
        }
        var collection = api.path("paths").path("/api/v1/todos");
        assertThat(collection.path("post").path("responses").path("201").path("headers").has("Location")).isTrue();
        assertThat(collection.path("get").path("responses").path("200").path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/TodoPageResponse");
        var parameters = collection.path("get").path("parameters");
        assertThat(parameterNames(parameters)).containsExactlyInAnyOrder("date", "from", "to", "status", "page", "size");
        assertThat(parameter(parameters, "page").path("schema").path("default").asInt()).isZero();
        assertThat(parameter(parameters, "size").path("schema").path("default").asInt()).isEqualTo(20);
        assertThat(parameter(parameters, "status").toString()).contains("PENDING", "IN_PROGRESS", "PAUSED", "COMPLETED");
        var itemResponses = api.path("paths").path("/api/v1/todos/{todoId}");
        assertThat(names(itemResponses.path("put").path("responses"))).contains("200", "404", "409");
        assertThat(names(itemResponses.path("delete").path("responses"))).contains("204", "404", "409");
        assertThat(itemResponses.path("delete").path("responses").path("204").path("content").isMissingNode()).isTrue();
        assertThat(schemas.path("TodoResponse").path("properties").path("actualDurationSeconds").path("format").asText()).isEqualTo("int64");
    }

    private MockHttpServletRequestBuilder routeRequest(String route) throws Exception {
        String[] parts = route.split(" ", 2);
        var request = request(HttpMethod.valueOf(parts[0]), parts[1]);
        if (route.endsWith("/calendar")) {
            request.param("from", "2026-10-01").param("to", "2026-10-31");
        }
        if (route.equals("POST /api/v1/todos") || parts[0].equals("PUT")) {
            request.contentType("application/json").content(json.writeValueAsString(timedInput()));
        }
        return request;
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + accessToken);
    }

    private org.springframework.mock.web.MockHttpServletResponse perform(MockHttpServletRequestBuilder request, Map<String, Object> body) throws Exception {
        if (body != null) request.contentType("application/json").content(json.writeValueAsString(body));
        return mvc.perform(request).andReturn().getResponse();
    }

    private Map<String, Object> timedInput() {
        var body = new LinkedHashMap<String, Object>();
        body.put("title", "TODO API 설계");
        body.put("memo", null);
        String date = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(1).toString();
        body.put("startDate", date);
        body.put("startTime", "09:00:00");
        body.put("dueDate", date);
        body.put("dueTime", "10:00:00");
        body.put("allDay", false);
        body.put("userEstimatedDurationMinutes", 30);
        return body;
    }

    private List<String> names(JsonNode node) {
        return new ArrayList<>(node.propertyNames());
    }

    private List<String> parameterNames(JsonNode parameters) {
        var names = new ArrayList<String>();
        for (var parameter : parameters) names.add(parameter.path("name").asText());
        return names;
    }

    private JsonNode parameter(JsonNode parameters, String name) {
        for (var parameter : parameters) if (parameter.path("name").asText().equals(name)) return parameter;
        throw new AssertionError("OpenAPI parameter 누락: " + name);
    }
}
