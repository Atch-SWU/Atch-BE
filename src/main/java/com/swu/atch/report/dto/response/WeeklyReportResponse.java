package com.swu.atch.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 주간 집계. 미래 날짜 제외, 완료율은 일별 비율 평균이 아닌 배정 수 기준
@Schema(description = "주간 집계. 미래 날짜 제외, 완료율은 일별 비율 평균이 아닌 배정 수 기준")
public record WeeklyReportResponse(
        @Schema(description = "조회 날짜가 속한 주의 월요일입니다.")
        LocalDate weekStart,
        @Schema(description = "실제 조회 범위의 시작 날짜(서울 기준, 포함)입니다.")
        LocalDate from,
        @Schema(description = "실제 조회 범위의 끝 날짜(서울 기준, 포함)입니다.")
        LocalDate to,
        @Schema(description = "주간 집계에 포함된 날짜별 결과 목록입니다.")
        List<DailyReportResponse> days,
        @Schema(description = "해당 날짜 또는 기간에 배정된 투두 수입니다. 집계가 미확정이거나 자료가 없으면 null일 수 있습니다.")
        Long assignedCount,
        @Schema(description = "배정된 투두 중 완료한 투두 수입니다.")
        Long completedAssignedCount,
        @Schema(description = "완료율(0~1). 예: 0.75는 75%입니다. 배정이 없거나 집계가 미확정이면 null입니다.")
        Double completionRate,
        @Schema(description = "해당 완료일 기준 현재 유효한 완료 투두 수입니다. 완료 취소·삭제 시 달라질 수 있습니다.")
        Long completedTodoCount,
        @Schema(description = "조회 기간의 총 집중시간(초)입니다.")
        Long totalFocusDurationSeconds,
        @Schema(description = "주간 평균 계산에 포함된 경과 날짜 수입니다. 집중시간이 0인 날짜도 포함합니다.")
        int elapsedDayCount,
        @Schema(description = "경과 날짜 수로 나눈 하루 평균 집중시간(초)입니다.")
        Double averageDailyFocusDurationSeconds,
        @Schema(description = "조회·계산에 사용한 기준 시각(UTC)입니다. 새로 조회해도 보존된 계산 결과는 원래 기준 시각을 유지할 수 있습니다.")
        Instant asOf
) {

}

