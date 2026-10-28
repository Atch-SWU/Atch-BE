package com.swu.atch.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.todo.domain.SelectedEstimateType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 제품의 예상·실제시간 비교. 전략별 실험 실행·연구용 정확도 지표는 포함하지 않음
@Schema(description = "제품의 예상·실제시간 비교. 전략별 실험 실행·연구용 정확도 지표는 포함하지 않음")
public record TimeComparisonPageResponse(
        LocalDate from, LocalDate to, List<TimeComparisonItem> items, int page, int size,
        long totalElements, int totalPages, boolean hasNext, Instant asOf
) {
    public record TimeComparisonItem(Long todoId, String title, Instant completedAt,
                       int userEstimatedDurationMinutes, Integer aiEstimatedDurationMinutes,
                       SelectedEstimateType selectedEstimateType, long actualDurationSeconds,
                       @Schema(description = "실제 초 - 사용자 예상 분*60. 음수 허용") long userErrorSeconds,
                       @Schema(description = "실제 초 - AI 예상 분*60. AI 제안이 없으면 null") Long aiErrorSeconds) { }
}

