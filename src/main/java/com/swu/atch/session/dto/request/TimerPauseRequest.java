package com.swu.atch.session.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 현재 실행 구간 일시정지 요청
@Schema(description = "현재 실행 구간 일시정지 요청")
public record TimerPauseRequest(
        @NotNull @Positive @Schema(description = "현재 실행 중인 작업 세션 ID. 현재 타이머 조회 응답의 sessionId를 그대로 보냅니다.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long sessionId
) {

}

