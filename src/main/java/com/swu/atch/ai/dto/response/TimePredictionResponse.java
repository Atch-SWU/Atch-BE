package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

// 단건 시간 제안. 10분 토큰 만료 후에도 동일 성공 결과를 외부 재호출 없이 재사용
@Schema(description = "단건 시간 제안. 10분 토큰 만료 후에도 동일 성공 결과를 외부 재호출 없이 재사용")
public record TimePredictionResponse(
        TimePredictionResult prediction, TimeEstimationProfileResponse profile,
        @Schema(description = "사용자·초안·입력·서버 제안값에 귀속된 검증 토큰. 인증 Access Token이 아님") String predictionToken,
        Instant expiresAt, Instant asOf
) {

}

