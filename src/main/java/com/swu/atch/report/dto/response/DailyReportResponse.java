package com.swu.atch.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

// 일간 집계. 배정 지표는 과거 Snapshot/오늘 현재 상태, 완료 건수는 현재 유효 완료 Todo 기준
@Schema(description = "일간 집계. 배정 지표는 과거 Snapshot/오늘 현재 상태, 완료 건수는 현재 유효 완료 Todo 기준")
public record DailyReportResponse(
        @Schema(description = "조회 대상 날짜(서울 기준)입니다.")
        LocalDate date,
        @Schema(description = "통계 상태. LIVE=오늘 실시간, FINALIZED=과거 확정, PENDING_FINALIZATION=확정 대기, UNAVAILABLE=자료 없음입니다. 미확정 값의 null을 0으로 표시하지 않습니다.")
        State state,
        @Schema(description = "확정 또는 현재 배정 수. 미확정·자료 없음은 null") Long assignedCount,
        @Schema(description = "배정된 투두 중 완료한 투두 수입니다.")
        Long completedAssignedCount,
        @Schema(description = "0~1 비율. 배정 0개 또는 미확정이면 null") Double completionRate,
        @Schema(description = "해당 날짜 완료 시각을 가진 현재 유효한 완료 Todo 수") Long completedTodoCount,
        @Schema(description = "배정된 투두 중 마감이 초과된 투두 수입니다.")
        Long overdueAssignedCount,
        @Schema(description = "종료된 작업 세션을 기준으로 집계한 집중시간(초)입니다.")
        Long closedFocusDurationSeconds,
        @Schema(description = "실행 중인 작업 세션에서 기준 시각까지 임시 집계한 집중시간(초)입니다.")
        Long liveFocusDurationSeconds,
        @Schema(description = "일시정지 횟수입니다.")
        Long pauseCount,
        @Schema(description = "일시정지 또는 전환 종료 이후 작업을 재개한 횟수입니다. 최초 시작은 포함하지 않습니다.")
        Long resumeCount,
        @Schema(description = "조회·계산에 사용한 기준 시각(UTC)입니다. 새로 조회해도 보존된 계산 결과는 원래 기준 시각을 유지할 수 있습니다.")
        Instant asOf
) {
    public enum State { LIVE, FINALIZED, PENDING_FINALIZATION, UNAVAILABLE }
}

