package com.swu.atch.session.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.todo.domain.TodoStatus;
import java.time.Instant;
import java.util.UUID;

// 서버 기준 현재 타이머. 집중 대상 없음은 IDLE과 null 식별자로 표현
@Schema(description = "서버 기준 현재 타이머. 집중 대상 없음은 IDLE과 null 식별자로 표현")
public record TimerStateResponse(
        State state, Long focusTodoId, String focusTodoTitle, TodoStatus todoStatus,
        Long sessionId, Long latestSessionId, Instant sessionStartedAt,
        @Schema(description = "종료된 Session의 저장 초 합계") long closedDurationSeconds,
        @Schema(description = "서버 시각 기준 현재 열린 구간 경과 초") long currentSessionElapsedSeconds,
        @Schema(description = "종료 구간 합계와 실행 중 경과 초의 합") long totalDurationSeconds,
        long remainingEstimatedSeconds, long estimateOverrunSeconds,
        SwitchWait pendingSwitch, Instant serverTime
) {
    public enum State { IDLE, RUNNING, PAUSED, SWITCH_WAITING }

    // 대기 정보는 사용자에게 귀속되며 만료·확인 시 다시 검증한다.
    public record SwitchWait(UUID switchId, Long targetTodoId, Instant requestedAt,
                             Instant readyAt, Instant expiresAt) { }
}

