package com.swu.atch.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 서울 시간대별 집중 구간과 완료일 요일별 완료 작업 소요시간
@Schema(description = "서울 시간대별 집중 구간과 완료일 요일별 완료 작업 소요시간")
public record FocusPatternResponse(
        LocalDate from, LocalDate to, List<HourlyBucket> hours, List<WeekdayBucket> weekdays, Instant asOf
) {
    public record HourlyBucket(int hour, long closedDurationSeconds, long liveDurationSeconds) { }
    public record WeekdayBucket(DayOfWeek dayOfWeek, long completedTodoCount,
                                long totalDurationSeconds, Double averageDurationSeconds) { }
}

