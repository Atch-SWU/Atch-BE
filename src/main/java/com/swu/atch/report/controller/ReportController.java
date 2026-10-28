package com.swu.atch.report.controller;

import com.swu.atch.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.swu.atch.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.swu.atch.report.dto.request.*;
import com.swu.atch.report.dto.response.*;
import org.springdoc.core.annotations.ParameterObject;

// 인증된 사용자와 서버 시각을 기준으로 처리한다. 외부 AI는 호출하지 않는다.
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "REPORT", description = "일간·주간·Snapshot·집중·예상시간 비교 계약")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "필수값·형식·범위·관계 검증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증 기반 장애 또는 일일 확정 복구 대기", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class ReportController {
    private final ReportService service;

    @GetMapping(value = "/reports/daily", produces = "application/json")
    @Operation(summary = "일간 집계", description = "FR-REPORT-001~004·007·010·011. 생략 시 서울 오늘입니다. 과거 배정·완료율은 Snapshot, 현재 날짜는 LIVE입니다. 배정 0개의 완료율은 null, 미확정·자료 없음은 별도 상태와 null로 표현합니다. 완료 건수는 해당 완료일의 현재 유효 완료 Todo 기준이므로 완료 취소·삭제 시 변할 수 있습니다. 최근 3년 이내, 미래 날짜 제외, from<=to만 허용합니다.")
    @ApiResponse(responseCode = "200", description = "일간 집계", content = @Content(schema = @Schema(implementation = DailyReportResponse.class)))
    public DailyReportResponse daily(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject ReportDateQuery query
    ) {
        return service.daily(id(jwt), stamp(jwt), query);
    }

    @GetMapping(value = "/reports/weekly", produces = "application/json")
    @Operation(summary = "주간 집계", description = "입력 날짜가 속한 월요일 시작 주를 조회하며 미래 날짜는 제외합니다. 주간 완료율은 배정 수 합계 대비 완료 수 합계입니다. 평균 일간 집중시간은 0분 날짜를 포함한 조회 경과 일수로 나눕니다. 최근 3년 이내, 미래 날짜 제외, from<=to만 허용합니다.")
    @ApiResponse(responseCode = "200", description = "주간 집계", content = @Content(schema = @Schema(implementation = WeeklyReportResponse.class)))
    public WeeklyReportResponse weekly(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject ReportDateQuery query
    ) {
        return service.weekly(id(jwt), stamp(jwt), query);
    }

    @GetMapping(value = "/reports/daily/todos", produces = "application/json")
    @Operation(summary = "일별 배정 이력", description = "FR-REPORT-011. 과거 확정 상태·일정·이월 여부를 보존합니다. currentTitle은 조회 시점 제목이며 과거 제목이 아닙니다. wasRolledOver는 해당 날짜로 들어온 여부입니다. 자동 이월·Snapshot 생성은 내부 처리이며 공개 쓰기 API를 만들지 않습니다. 최근 3년 이내, 미래 날짜 제외, from<=to만 허용합니다.")
    @ApiResponse(responseCode = "200", description = "일별 배정 이력", content = @Content(schema = @Schema(implementation = DailyTodoHistoryResponse.class)))
    public DailyTodoHistoryResponse history(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject ReportDateQuery query
    ) {
        return service.history(id(jwt), stamp(jwt), query);
    }

    @GetMapping(value = "/reports/focus-patterns", produces = "application/json")
    @Operation(summary = "시간대·요일별 집중 패턴", description = "FR-REPORT-004·008~010. from/to 양 끝 포함, 서울 날짜·시간대를 사용합니다. 열린 구간은 asOf까지만 별도 임시 초로 반환합니다. 날짜·시간대 분할 초 합계는 원래 Session 저장 초와 일치시킵니다. 요일 통계는 완료일의 서울 요일에 완료 작업 전체 실제 초를 배정합니다. 최근 3년 이내, 미래 날짜 제외, from<=to만 허용합니다.")
    @ApiResponse(responseCode = "200", description = "시간대·요일별 집중 패턴", content = @Content(schema = @Schema(implementation = FocusPatternResponse.class)))
    public FocusPatternResponse patterns(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject ReportRangeQuery query
    ) {
        return service.patterns(id(jwt), stamp(jwt), query);
    }

    @GetMapping(value = "/reports/time-comparisons", produces = "application/json")
    @Operation(summary = "사용자·AI 예상시간과 실제시간 비교", description = "FR-REPORT-005·006. 현재 유효 완료 Todo를 완료 시각·ID 내림차순으로 조회합니다. 오차 초는 실제-예상이며 음수를 허용합니다. AI 제안이 없으면 AI 필드는 null입니다. 현재 Todo의 예상값을 조회하며 과거 확정 예상값으로 표시하지 않습니다. 전략별 비교 실험·MAE/MAPE·미정 정확도 백분율은 제외합니다. 최근 3년 이내, 미래 날짜 제외, from<=to만 허용합니다.")
    @ApiResponse(responseCode = "200", description = "사용자·AI 예상시간과 실제시간 비교", content = @Content(schema = @Schema(implementation = TimeComparisonPageResponse.class)))
    public TimeComparisonPageResponse comparisons(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject TimeComparisonQuery query
    ) {
        return service.comparisons(id(jwt), stamp(jwt), query);
    }

    private Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private String stamp(Jwt jwt) { return jwt.getClaimAsString("credential_stamp"); }
}

