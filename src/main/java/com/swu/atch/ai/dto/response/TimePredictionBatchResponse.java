package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 작업별 제안과 서버 계산 합계. 하나의 전체 시간을 사용자 시간 비율로 재분배하지 않음
@Schema(description = "작업별 제안과 서버 계산 합계. 하나의 전체 시간을 사용자 시간 비율로 재분배하지 않음")
public record TimePredictionBatchResponse(
        UUID decompositionDraftId, List<TimePredictionResult> predictions,
        TimeEstimationProfileResponse profile, long totalUserEstimatedDurationMinutes,
        long totalAiEstimatedDurationMinutes, String predictionToken, Instant expiresAt, Instant asOf
) {

}

