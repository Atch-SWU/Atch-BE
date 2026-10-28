package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

// 기능별 1회 성공 사용 상태. 제한·예약·결과는 DB에 보존
@Schema(description = "기능별 1회 성공 사용 상태. 제한·예약·결과는 DB에 보존")
public record AiUsageResponse(
        State state,
        @Schema(description = "실패 후 재시도 가능 여부. 성공 결과 재조회는 재호출과 구분") boolean retryAllowed,
        Instant succeededAt, boolean cachedResultAvailable
) {
    public enum State { NOT_USED, IN_PROGRESS, SUCCEEDED, RETRYABLE_FAILURE, NOT_APPLICABLE, LEGACY_UNKNOWN }
}

