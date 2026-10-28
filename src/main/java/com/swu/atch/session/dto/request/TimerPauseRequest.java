package com.swu.atch.session.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 현재 실행 구간 일시정지 요청
@Schema(description = "현재 실행 구간 일시정지 요청")
public record TimerPauseRequest(
        @NotNull @Positive @Schema(description = "종료할 본인 Todo의 열린 Session ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long sessionId
) {

}

