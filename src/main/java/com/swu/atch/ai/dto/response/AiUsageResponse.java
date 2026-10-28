package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

// 기능별 1회 성공 사용 상태. 제한·예약·결과는 DB에 보존
@Schema(description = "기능별 1회 성공 사용 상태. 제한·예약·결과는 DB에 보존")
public record AiUsageResponse(
        @Schema(description = "AI 사용 상태. NOT_USED=미사용, IN_PROGRESS=처리 중, SUCCEEDED=성공 1회 사용, RETRYABLE_FAILURE=실패 후 재시도 가능, NOT_APPLICABLE=대상 아님, LEGACY_UNKNOWN=과거 사용 이력 불명입니다.")
        State state,
        @Schema(description = "실패 후 재시도 가능 여부. 성공 결과 재조회는 재호출과 구분") boolean retryAllowed,
        @Schema(description = "AI 요청이 성공한 시각(UTC). 아직 성공하지 않았으면 null입니다.")
        Instant succeededAt,
        @Schema(description = "보존된 성공 결과를 다시 조회할 수 있는지 여부입니다.")
        boolean cachedResultAvailable
) {
    public enum State { NOT_USED, IN_PROGRESS, SUCCEEDED, RETRYABLE_FAILURE, NOT_APPLICABLE, LEGACY_UNKNOWN }
}

