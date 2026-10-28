package com.swu.atch.session.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 새 Session을 생성하는 재개 요청
@Schema(description = "새 Session을 생성하는 재개 요청")
public record TimerResumeRequest(
        @NotNull @Positive @Schema(description = "재개 기준인 마지막 종료 Session ID. 상태가 바뀌었으면 409", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long lastSessionId
) {

}

