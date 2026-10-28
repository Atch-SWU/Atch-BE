package com.swu.atch.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 주간 집계. 미래 날짜 제외, 완료율은 일별 비율 평균이 아닌 배정 수 기준
@Schema(description = "주간 집계. 미래 날짜 제외, 완료율은 일별 비율 평균이 아닌 배정 수 기준")
public record WeeklyReportResponse(
        LocalDate weekStart, LocalDate from, LocalDate to, List<DailyReportResponse> days,
        Long assignedCount, Long completedAssignedCount, Double completionRate,
        Long completedTodoCount, Long totalFocusDurationSeconds,
        int elapsedDayCount, Double averageDailyFocusDurationSeconds, Instant asOf
) {

}

