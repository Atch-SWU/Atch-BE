package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

// 서버 발급 작업 초안. 분해·시간 예측의 성공 사용 횟수는 각각 관리
@Schema(description = "서버 발급 작업 초안. 분해·시간 예측의 성공 사용 횟수는 각각 관리")
public record AiDraftResponse(
        @Schema(description = "이 작업에 연결된 서버 발급 AI 초안 UUID. 후속 예측·저장 요청에서 같은 값을 사용합니다.")
        UUID draftId,
        @Schema(description = "연결된 본인 투두 ID. 저장 전 신규 작업 초안은 null입니다.")
        Long todoId,
        @Schema(description = "이 응답에 포함된 임시 초안·제안 또는 대기 요청의 만료 시각(UTC)입니다.")
        Instant expiresAt,
        @Schema(description = "이 작업의 AI 세부 분량 나누기 사용 상태입니다.")
        AiUsageResponse decompositionUsage,
        @Schema(description = "이 작업의 AI 시간 예측 사용 상태입니다.")
        AiUsageResponse timePredictionUsage
) {

}

