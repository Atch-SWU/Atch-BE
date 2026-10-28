package com.swu.atch.session.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

// 타이머 시작 요청. 기준 구간 확인과 상태 전환은 후속 Service에서 처리
@Schema(description = "타이머 시작 요청. 기준 구간 확인과 상태 전환은 후속 Service에서 처리")
public record TimerStartRequest(
        @Positive @Schema(description = "마지막으로 확인한 Session ID. 한 번도 실행하지 않은 Todo만 null", example = "1")
        Long expectedLastSessionId
) {

}

