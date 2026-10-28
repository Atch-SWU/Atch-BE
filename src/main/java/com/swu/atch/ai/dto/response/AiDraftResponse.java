package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

// 서버 발급 작업 초안. 분해·시간 예측의 성공 사용 횟수는 각각 관리
@Schema(description = "서버 발급 작업 초안. 분해·시간 예측의 성공 사용 횟수는 각각 관리")
public record AiDraftResponse(
        UUID draftId, Long todoId, Instant expiresAt,
        AiUsageResponse decompositionUsage, AiUsageResponse timePredictionUsage
) {

}

