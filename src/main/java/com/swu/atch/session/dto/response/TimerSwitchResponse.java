package com.swu.atch.session.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

// 전환 요청 결과. 제한 적용 시 WAITING, 제한이 꺼져 있으면 STARTED
@Schema(description = "전환 요청 결과. 제한 적용 시 WAITING, 제한이 꺼져 있으면 STARTED")
public record TimerSwitchResponse(
        @Schema(description = "서버가 발급한 작업 전환 요청 UUID. 확정·취소 API 경로에 그대로 사용합니다.")
        UUID switchId,
        @Schema(description = "작업 전환 상태. WAITING=대기 중, STARTED=전환 후 타이머 시작입니다.")
        State state,
        @Schema(description = "전환 전 집중 대상 투두 ID입니다.")
        Long sourceTodoId,
        @Schema(description = "전환해서 시작하려는 다른 투두의 ID입니다.")
        Long targetTodoId,
        @Schema(description = "작업 전환을 요청한 시각(UTC)입니다.")
        Instant requestedAt,
        @Schema(description = "작업 전환을 확정할 수 있는 가장 이른 시각(UTC). 이 시각 이후 확정 API를 호출해야 실제 전환됩니다.")
        Instant readyAt,
        @Schema(description = "이 응답에 포함된 임시 초안·제안 또는 대기 요청의 만료 시각(UTC)입니다.")
        Instant expiresAt,
        @Schema(description = "전환 처리 후의 현재 타이머 상태입니다.")
        TimerStateResponse timer
) {
    public enum State { WAITING, STARTED }
}

