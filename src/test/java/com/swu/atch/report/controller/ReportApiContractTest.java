package com.swu.atch.report.controller;

import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.jwt.JwtTokenService;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.report.dto.request.TimeComparisonQuery;
import com.swu.atch.report.dto.response.DailyReportResponse;
import com.swu.atch.report.dto.response.DailyTodoHistoryResponse;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import com.swu.atch.todo.domain.TodoStatus;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// 통계 계산·이월·확정 성공 테스트가 아닌 인증·DTO·문서 계약 테스트다.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "app.api.contracts.enabled=true"})
@Transactional
class ReportApiContractTest extends IsolatedAuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    @Autowired AuthAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountCredentials credentials;
    @Autowired JwtTokenService tokens;
    private String accessToken;

    @BeforeEach
    void localAccount() {
        var user = users.save(new User("통계계약"));
        var account = accounts.save(AuthAccount.local(user, UUID.randomUUID() + "@atch.invalid", encoder.encode("Contract123!")));
        accessToken = tokens.issueAccessToken(user.getId(), credentials.stamp(account));
    }

    static Stream<String> routes() {
        return Stream.of("/api/v1/reports/daily", "/api/v1/reports/weekly", "/api/v1/reports/daily/todos",
                "/api/v1/reports/focus-patterns", "/api/v1/reports/time-comparisons");
    }

    @ParameterizedTest
    @MethodSource("routes")
    void everyReadRequiresAuthenticationAndValidBindingReaches501(String path) throws Exception {
        assertThat(mvc.perform(get(path)).andReturn().getResponse().getStatus()).isEqualTo(401);
        var response = mvc.perform(get(path).param("date", "2026-10-29")
                .param("from", "2026-10-01").param("to", "2026-10-29")
                .header("Authorization", "Bearer " + accessToken)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(501);
        assertThat(json.readTree(response.getContentAsString()).path("code").asText()).isEqualTo("NOT_IMPLEMENTED");
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(
                Arguments.of("/api/v1/reports/daily", "date", "invalid"),
                Arguments.of("/api/v1/reports/weekly", "date", "2026-99-99"),
                Arguments.of("/api/v1/reports/focus-patterns", "from", "2026-10-01"),
                Arguments.of("/api/v1/reports/time-comparisons", "page", "-1"),
                Arguments.of("/api/v1/reports/time-comparisons", "size", "101")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void malformedOrMissingScalarInputsAre400(String path, String field, String value) throws Exception {
        var request = get(path).header("Authorization", "Bearer " + accessToken);
        if (path.endsWith("time-comparisons")) request.param("from", "2026-10-01").param("to", "2026-10-29");
        request.param(field, value);
        assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void liveAndUnfinalizedReportsKeepAbsenceDifferentFromZero() throws Exception {
        var date = LocalDate.of(2026, 10, 28);
        var asOf = Instant.parse("2026-10-28T15:00:00Z");
        var empty = new DailyReportResponse(date, DailyReportResponse.State.FINALIZED,
                0L, 0L, null, 0L, 0L, 0L, 0L, 0L, 0L, asOf);
        var pending = new DailyReportResponse(date, DailyReportResponse.State.PENDING_FINALIZATION,
                null, null, null, null, null, null, null, null, null, asOf);
        var zero = json.readTree(json.writeValueAsString(empty));
        var missing = json.readTree(json.writeValueAsString(pending));
        assertThat(zero.path("assignedCount").asLong()).isZero();
        assertThat(zero.path("completionRate").isNull()).isTrue();
        assertThat(missing.path("assignedCount").isNull()).isTrue();
        assertThat(missing.path("state").asText()).isEqualTo("PENDING_FINALIZATION");
        assertThat(missing.path("asOf").asText()).isEqualTo("2026-10-28T15:00:00Z");
    }

    @Test
    void snapshotResponseUsesExplicitCurrentTitleAndPreservedSnapshotFields() throws Exception {
        var instant = Instant.parse("2026-10-28T15:00:00Z");
        var item = new DailyTodoHistoryResponse.SnapshotItem(1L, "현재 제목", TodoStatus.PAUSED,
                instant.minusSeconds(7200), instant.minusSeconds(3600), null, true, true, instant);
        var tree = json.readTree(json.writeValueAsString(item));
        assertThat(tree.path("currentTitle").asText()).isEqualTo("현재 제목");
        assertThat(tree.has("titleSnapshot")).isFalse();
        assertThat(tree.has("userEstimatedDurationMinutesSnapshot")).isFalse();
        assertThat(tree.path("statusAtDayEnd").asText()).isEqualTo("PAUSED");
        assertThat(tree.path("completedAtSnapshot").isNull()).isTrue();
        assertThat(tree.path("wasRolledOver").asBoolean()).isTrue();
        assertThat(tree.path("wasOverdue").asBoolean()).isTrue();
        var query = new TimeComparisonQuery(null, null, null, null);
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
    }

    @Test
    void openApiDescribesReadOnlyStatisticsAndNullableCompletionRate() throws Exception {
        var response = mvc.perform(get("/v3/api-docs")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        var api = json.readTree(response.getContentAsString());
        for (var path : routes().toList()) {
            var item = api.path("paths").path(path);
            assertThat(item.has("get")).isTrue();
            assertThat(item.has("post")).isFalse();
            assertThat(item.path("get").path("description").asText()).contains("현재 501");
            assertThat(item.path("get").path("security").toString()).contains("bearerAuth");
        }
        var properties = api.path("components").path("schemas").path("DailyReportResponse").path("properties");
        assertThat(properties.has("completionRate")).isTrue();
        assertThat(properties.path("closedFocusDurationSeconds").path("format").asText()).isEqualTo("int64");
        assertThat(api.path("paths").has("/api/v1/reports/finalize")).isFalse();
        // 서로 다른 응답이 같은 단순 이름으로 합쳐지면 클라이언트 코드 생성이 잘못된다.
        var schemas = api.path("components").path("schemas");
        String snapshotRef = schemas.path("DailyTodoHistoryResponse").path("properties").path("items").path("items").path("$ref").asText();
        String comparisonRef = schemas.path("TimeComparisonPageResponse").path("properties").path("items").path("items").path("$ref").asText();
        assertThat(snapshotRef).isNotEqualTo(comparisonRef);
        assertThat(schemas.path(snapshotRef.substring(snapshotRef.lastIndexOf('/') + 1)).path("properties").propertyNames())
                .contains("currentTitle", "statusAtDayEnd", "wasRolledOver").doesNotContain("userErrorSeconds");
        assertThat(schemas.path(comparisonRef.substring(comparisonRef.lastIndexOf('/') + 1)).path("properties").propertyNames())
                .contains("userEstimatedDurationMinutes", "actualDurationSeconds", "userErrorSeconds").doesNotContain("statusAtDayEnd");
    }
}

