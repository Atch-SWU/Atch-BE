package com.swu.atch.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 서울 시간대별 집중 구간과 완료일 요일별 완료 작업 소요시간
@Schema(description = "서울 시간대별 집중 구간과 완료일 요일별 완료 작업 소요시간")
public record FocusPatternResponse(
        @Schema(description = "실제 조회 범위의 시작 날짜(서울 기준, 포함)입니다.")
        LocalDate from,
        @Schema(description = "실제 조회 범위의 끝 날짜(서울 기준, 포함)입니다.")
        LocalDate to,
        @Schema(description = "서울 기준 시간대별 집중시간 목록입니다.")
        List<HourlyBucket> hours,
        @Schema(description = "서울 기준 요일별 완료·집중시간 목록입니다.")
        List<WeekdayBucket> weekdays,
        @Schema(description = "조회·계산에 사용한 기준 시각(UTC)입니다. 새로 조회해도 보존된 계산 결과는 원래 기준 시각을 유지할 수 있습니다.")
        Instant asOf
) {
    public record HourlyBucket(
            @Schema(description = "서울 기준 시간대(0~23)입니다. 예: 9는 09:00~10:00 구간입니다.")
            int hour,
            @Schema(description = "해당 서울 시간대에 속하는 종료 세션의 집중시간 합계(초)입니다.")
            long closedDurationSeconds,
            @Schema(description = "해당 서울 시간대에 속하는 실행 중 세션의 임시 집중시간(초). 조회 기준 시각까지만 집계합니다.")
            long liveDurationSeconds) { }
    public record WeekdayBucket(
            @Schema(description = "요일. MONDAY=월요일부터 SUNDAY=일요일까지입니다.")
            DayOfWeek dayOfWeek,
            @Schema(description = "해당 완료일 기준 현재 유효한 완료 투두 수입니다. 완료 취소·삭제 시 달라질 수 있습니다.")
            long completedTodoCount,
            @Schema(description = "해당 요일에 완료한 작업들의 누적 실제 집중시간 합계(초)입니다.")
            long totalDurationSeconds,
            @Schema(description = "해당 요일에 완료한 작업의 평균 실제 집중시간(초)입니다.")
            Double averageDurationSeconds) { }
}

