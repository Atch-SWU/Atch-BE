package com.swu.atch.session.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 새 Session을 생성하는 재개 요청
@Schema(description = "새 Session을 생성하는 재개 요청")
public record TimerResumeRequest(
        @NotNull @Positive @Schema(description = "재개할 투두에서 마지막으로 종료된 작업 세션 ID. 해당 투두의 최신 세션 ID를 그대로 보냅니다.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long lastSessionId
) {

}

