package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 작업별 제안과 서버 계산 합계. 하나의 전체 시간을 사용자 시간 비율로 재분배하지 않음
@Schema(description = "작업별 제안과 서버 계산 합계. 하나의 전체 시간을 사용자 시간 비율로 재분배하지 않음")
public record TimePredictionBatchResponse(
        @Schema(description = "분해 원본의 초안 UUID. 묶음 예측·최종 저장 요청에 그대로 사용합니다.")
        UUID decompositionDraftId,
        @Schema(description = "각 세부 작업의 시간 예측 결과 목록입니다.")
        List<TimePredictionResult> predictions,
        @Schema(description = "예측에 사용한 개인화 시간 예측 프로필입니다.")
        TimeEstimationProfileResponse profile,
        @Schema(description = "각 작업에 사용자가 직접 입력한 예상시간의 합계(분)입니다.")
        long totalUserEstimatedDurationMinutes,
        @Schema(description = "각 작업의 AI 제안시간 합계(분)입니다. 전체 시간을 비율로 나눈 값이 아닙니다.")
        long totalAiEstimatedDurationMinutes,
        @Schema(description = "서버가 발급한 AI 제안 확인용 토큰. 투두 저장 시 estimateChoice에 복사합니다. 로그인 토큰이 아니며 expiresAt까지 유효합니다.")
        String predictionToken,
        @Schema(description = "이 응답에 포함된 임시 초안·제안 또는 대기 요청의 만료 시각(UTC)입니다.")
        Instant expiresAt,
        @Schema(description = "조회·계산에 사용한 기준 시각(UTC)입니다. 새로 조회해도 보존된 계산 결과는 원래 기준 시각을 유지할 수 있습니다.")
        Instant asOf
) {

}

