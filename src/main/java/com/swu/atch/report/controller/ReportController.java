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
@Tag(name = "05. [프론트] 통계 · 기록")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "필수값·형식·범위·관계 검증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증 기반 장애 또는 일일 확정 복구 대기", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class ReportController {
    private final ReportService service;

    @GetMapping(value = "/reports/daily", produces = "application/json")
    @Operation(summary = "일간 집계", description = """
            ### 언제 호출하나요?
            일간 통계 화면에서 `date`로 조회합니다. 생략하면 서울 기준 오늘이며 최근 3년 이내의 미래가 아닌 날짜만 가능합니다.

            ### 응답 표시

            - 오늘은 `LIVE`, 과거 확정 기록은 `FINALIZED`입니다. 확정 대기·자료 없음 상태도 구분합니다.
            - `completionRate`는 0~1 비율이며 배정이 없거나 미확정이면 null입니다. null을 완료율 0%로 바꾸지 않습니다.
            - 과거 배정·완료율은 날짜별 확정 기록을 사용합니다.
            - `completedTodoCount`는 해당 날짜에 완료한 현재 유효 투두 수이므로 완료 취소·삭제 시 달라질 수 있습니다.
            """)
    @ApiResponse(responseCode = "200", description = "일간 집계", content = @Content(schema = @Schema(implementation = DailyReportResponse.class)))
    public DailyReportResponse daily(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject ReportDateQuery query
    ) {
        return service.daily(id(jwt), stamp(jwt), query);
    }

    @GetMapping(value = "/reports/weekly", produces = "application/json")
    @Operation(summary = "주간 집계", description = """
            ### 언제 호출하나요?
            주간 통계 화면에서 `date`로 조회합니다. 생략하면 오늘이 속한 주이며 서울 기준 월요일부터 조회합니다. 최근 3년 이내의 미래가 아닌 날짜만 가능합니다.

            ### 계산 기준

            - 이번 주는 오늘까지의 날짜를 포함하고 미래 날짜는 제외합니다.
            - 주간 완료율은 일별 완료율 평균이 아닌 전체 배정 수 대비 완료 수입니다.
            - 하루 평균 집중시간은 집중시간이 0인 날도 포함한 경과 날짜 수로 나눕니다.
            """)
    @ApiResponse(responseCode = "200", description = "주간 집계", content = @Content(schema = @Schema(implementation = WeeklyReportResponse.class)))
    public WeeklyReportResponse weekly(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject ReportDateQuery query
    ) {
        return service.weekly(id(jwt), stamp(jwt), query);
    }

    @GetMapping(value = "/reports/daily/todos", produces = "application/json")
    @Operation(summary = "일별 배정 이력", description = """
            ### 언제 호출하나요?
            하루에 배정되었던 투두 기록을 보여줄 때 `date`로 조회합니다. 생략하면 서울 기준 오늘이며 최근 3년 이내의 미래가 아닌 날짜만 가능합니다.

            ### 응답 표시

            - 과거 확정 상태·일정·이월 여부를 조회합니다.
            - `currentTitle`은 현재 태스크명입니다. 과거 당시의 태스크명으로 표시하지 않습니다.
            - `wasRolledOver`는 해당 날짜로 들어온 이월 작업이었는지 나타냅니다.
            - 기록 확정과 자동 이월은 서버 내부 처리입니다. 프론트에서 생성·이월 API를 따로 호출하지 않습니다.
            """)
    @ApiResponse(responseCode = "200", description = "일별 배정 이력", content = @Content(schema = @Schema(implementation = DailyTodoHistoryResponse.class)))
    public DailyTodoHistoryResponse history(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject ReportDateQuery query
    ) {
        return service.history(id(jwt), stamp(jwt), query);
    }

    @GetMapping(value = "/reports/focus-patterns", produces = "application/json")
    @Operation(summary = "시간대·요일별 집중 패턴", description = """
            ### 입력
            `from`과 `to`를 모두 보내며 양 끝 날짜를 포함합니다. 서울 기준 최근 3년 이내, 미래 제외, from≤to 조건입니다.

            ### 응답 표시

            - `hours`: 서울 시간대별 집중시간입니다. 종료된 구간과 현재 실행 중인 임시 시간을 구분합니다.
            - `weekdays`: 완료한 날의 서울 요일별 완료 작업 수·누적 실제시간입니다.
            - 실행 중인 구간은 `asOf`까지만 집계합니다. 자정을 지나는 세션도 날짜·시간대에 맞게 서버가 나누므로 프론트에서 재분배할 필요가 없습니다.
            """)
    @ApiResponse(responseCode = "200", description = "시간대·요일별 집중 패턴", content = @Content(schema = @Schema(implementation = FocusPatternResponse.class)))
    public FocusPatternResponse patterns(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute @ParameterObject ReportRangeQuery query
    ) {
        return service.patterns(id(jwt), stamp(jwt), query);
    }

    @GetMapping(value = "/reports/time-comparisons", produces = "application/json")
    @Operation(summary = "사용자·AI 예상시간과 실제시간 비교", description = """
            ### 입력
            `from`과 `to`를 모두 보내며 양 끝 날짜를 포함합니다. 서울 기준 최근 3년 이내, 미래 제외, from≤to 조건입니다. page는 0부터 시작합니다.

            ### 응답 표시

            - 유효한 완료 투두를 최근 완료 순서로 반환합니다.
            - 직접 입력 예상시간·AI 예상시간은 분, 실제시간·오차는 초입니다.
            - 오차는 실제−예상입니다. 양수는 예상보다 오래 걸림, 음수는 예상보다 일찍 끝남을 뜻합니다.
            - AI 제안이 없으면 AI 관련 필드는 null입니다.
            - 현재 투두의 예상값이므로 과거 확정 당시의 예상값으로 표시하지 않습니다.
            """)
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

