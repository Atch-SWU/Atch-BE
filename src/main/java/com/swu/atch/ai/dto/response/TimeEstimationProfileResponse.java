package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

// 저장 Profile 또는 이번 계산 결과. 신뢰도 점수는 예측 성공 확률이 아님
@Schema(description = "저장 Profile 또는 이번 계산 결과. 신뢰도 점수는 예측 성공 확률이 아님")
public record TimeEstimationProfileResponse(
        @Schema(description = "프로필 상태. AVAILABLE=계산 결과 있음, NO_HISTORY=유효 완료 이력 없음, NOT_CALCULATED=아직 계산하지 않음입니다.")
        State state,
        @Schema(description = "프로필 값의 출처. STORED_SNAPSHOT=기존 저장본, FRESH_COMPUTATION=이번 요청에서 새로 계산한 값입니다.")
        Source source,
        @Schema(description = "저장된 개인화 프로필이 존재하는지 여부입니다.")
        boolean profileExists,
        @Schema(description = "사용자 시간 보정 배수. 직접 입력시간에 곱해 평소 예상 습관을 반영합니다. 유효 이력이 없으면 1.0입니다.")
        double durationCorrectionFactor,
        @Schema(description = "프로필 계산에 사용한 유효 완료 작업 수입니다.")
        int sampleCount,
        @Schema(description = "예상시간 대비 실제시간 비율의 변동 정도입니다. 내부 통계 지표이며 화면 필수 표시값은 아닙니다.")
        Double logRatioStddev,
        @Schema(description = "과소예측 비율(0~1). 실제시간이 직접 예상시간보다 길었던 유효 완료 작업의 비율입니다.")
        double underestimationRate,
        @Schema(description = "프로필의 내부 휴리스틱 신뢰도 점수입니다. AI 정답 확률로 표시하지 않습니다.")
        double profileReliabilityScore,
        @Schema(description = "프로필 신뢰도 등급입니다. LOW=낮음, MEDIUM=보통, HIGH=높음입니다.")
        Reliability reliability,
        @Schema(description = "마지막 저장·수정 시각(UTC)입니다.")
        Instant updatedAt,
        @Schema(description = "이번에 실제 계산한 기준 시각. 저장본 조회에서 근거가 없으면 null") Instant calculatedAsOf
) {
    public enum State { AVAILABLE, NO_HISTORY, NOT_CALCULATED }
    public enum Source { STORED_SNAPSHOT, FRESH_COMPUTATION }
    public enum Reliability { LOW, MEDIUM, HIGH }
}

