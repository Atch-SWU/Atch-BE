package com.swu.atch.session.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

// 전환 요청 결과. 제한 적용 시 WAITING, 제한이 꺼져 있으면 STARTED
@Schema(description = "전환 요청 결과. 제한 적용 시 WAITING, 제한이 꺼져 있으면 STARTED")
public record TimerSwitchResponse(
        UUID switchId, State state, Long sourceTodoId, Long targetTodoId,
        Instant requestedAt, Instant readyAt, Instant expiresAt, TimerStateResponse timer
) {
    public enum State { WAITING, STARTED }
}

