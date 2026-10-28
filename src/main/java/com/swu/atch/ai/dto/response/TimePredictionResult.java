package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.ai.domain.PredictionReasonCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 작업별 제안. Claude 보정 성공만 1회 사용으로 소비하고 Baseline 폴백은 재시도 허용
@Schema(description = "작업별 제안. Claude 보정 성공만 1회 사용으로 소비하고 Baseline 폴백은 재시도 허용")
public record TimePredictionResult(
        UUID draftId, Long todoId, int userEstimatedDurationMinutes,
        double durationCorrectionFactor, double baselinePredictionMinutes,
        int aiEstimatedDurationMinutes, double contextAdjustmentFactor,
        Mode mode, ClaudeStatus claudeStatus, SimilarTodoSearchResponse.VoyageStatus voyageStatus,
        List<PredictionReasonCode> reasonCodes, List<SimilarTodoSummary> similarTodos,
        AiUsageResponse usage, boolean cached,
        @Schema(description = "해당 결과 계산의 기준 시각. 재시도 묶음의 캐시 항목도 원래 시각 유지") Instant asOf, TimeEstimationProfileResponse profile
) {
    public enum Mode { HYBRID, STATISTICAL_BASELINE }
    public enum ClaudeStatus { APPLIED, DISABLED, TIMEOUT, RATE_LIMITED, INVALID_RESPONSE, REFUSED, TRUNCATED, UNAVAILABLE }
}

