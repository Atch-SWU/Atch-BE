package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

// 저장 Profile 또는 이번 계산 결과. 신뢰도 점수는 예측 성공 확률이 아님
@Schema(description = "저장 Profile 또는 이번 계산 결과. 신뢰도 점수는 예측 성공 확률이 아님")
public record TimeEstimationProfileResponse(
        State state, Source source, boolean profileExists, double durationCorrectionFactor,
        int sampleCount, Double logRatioStddev, double underestimationRate,
        double profileReliabilityScore, Reliability reliability, Instant updatedAt,
        @Schema(description = "이번에 실제 계산한 기준 시각. 저장본 조회에서 근거가 없으면 null") Instant calculatedAsOf
) {
    public enum State { AVAILABLE, NO_HISTORY }
    public enum Source { STORED_SNAPSHOT, FRESH_COMPUTATION }
    public enum Reliability { LOW, MEDIUM, HIGH }
}

