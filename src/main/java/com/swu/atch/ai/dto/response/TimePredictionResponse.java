package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

// 단건 시간 제안. 10분 토큰 만료 후에도 동일 성공 결과를 외부 재호출 없이 재사용
@Schema(description = "단건 시간 제안. 10분 토큰 만료 후에도 동일 성공 결과를 외부 재호출 없이 재사용")
public record TimePredictionResponse(
        @Schema(description = "단일 작업의 시간 예측 결과입니다.")
        TimePredictionResult prediction,
        @Schema(description = "예측에 사용한 개인화 시간 예측 프로필입니다.")
        TimeEstimationProfileResponse profile,
        @Schema(description = "서버가 발급한 AI 제안 확인용 토큰. 투두 저장 시 estimateChoice에 복사합니다. 로그인 토큰이 아니며 expiresAt까지 유효합니다.") String predictionToken,
        @Schema(description = "이 응답에 포함된 임시 초안·제안 또는 대기 요청의 만료 시각(UTC)입니다.")
        Instant expiresAt,
        @Schema(description = "조회·계산에 사용한 기준 시각(UTC)입니다. 새로 조회해도 보존된 계산 결과는 원래 기준 시각을 유지할 수 있습니다.")
        Instant asOf
) {

}

