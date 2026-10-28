package com.swu.atch.session.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

// 타이머 시작 요청. 기준 구간 확인과 상태 전환은 Service에서 처리
@Schema(description = "타이머 시작 요청. 기준 구간 확인과 상태 전환은 Service에서 처리")
public record TimerStartRequest(
        @Positive @Schema(description = "시작할 투두의 마지막 작업 세션 ID. 해당 투두의 세션 목록 등에서 받은 값을 보냅니다. 한 번도 시작하지 않은 투두만 null입니다. 다른 투두의 세션 ID를 보내지 않습니다.", example = "1")
        Long expectedLastSessionId
) {

}

