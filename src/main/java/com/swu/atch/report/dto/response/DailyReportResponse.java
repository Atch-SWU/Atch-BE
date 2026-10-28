package com.swu.atch.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

// 일간 집계. 배정 지표는 과거 Snapshot/오늘 현재 상태, 완료 건수는 현재 유효 완료 Todo 기준
@Schema(description = "일간 집계. 배정 지표는 과거 Snapshot/오늘 현재 상태, 완료 건수는 현재 유효 완료 Todo 기준")
public record DailyReportResponse(
        LocalDate date, State state,
        @Schema(description = "확정 또는 현재 배정 수. 미확정·자료 없음은 null") Long assignedCount,
        Long completedAssignedCount,
        @Schema(description = "0~1 비율. 배정 0개 또는 미확정이면 null") Double completionRate,
        @Schema(description = "해당 날짜 완료 시각을 가진 현재 유효한 완료 Todo 수") Long completedTodoCount,
        Long overdueAssignedCount, Long closedFocusDurationSeconds, Long liveFocusDurationSeconds,
        Long pauseCount, Long resumeCount, Instant asOf
) {
    public enum State { LIVE, FINALIZED, PENDING_FINALIZATION, UNAVAILABLE }
}

