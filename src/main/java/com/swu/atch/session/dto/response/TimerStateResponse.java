package com.swu.atch.session.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.todo.domain.TodoStatus;
import java.time.Instant;
import java.util.UUID;

// 서버 기준 현재 타이머. 집중 대상 없음은 IDLE과 null 식별자로 표현
@Schema(description = "서버 기준 현재 타이머. 집중 대상 없음은 IDLE과 null 식별자로 표현")
public record TimerStateResponse(
        @Schema(description = "타이머 상태. IDLE=집중 대상 없음, RUNNING=실행 중, PAUSED=일시정지, SWITCH_WAITING=작업 전환 대기입니다.")
        State state,
        @Schema(description = "현재 집중 대상 투두 ID. 일시정지 중에도 유지되며 대상이 없으면 null입니다.")
        Long focusTodoId,
        @Schema(description = "현재 집중 대상의 태스크명입니다.")
        String focusTodoTitle,
        @Schema(description = "현재 집중 대상 투두의 진행 상태입니다.")
        TodoStatus todoStatus,
        @Schema(description = "현재 실행 중인 작업 세션 ID. 일시정지 요청에 사용하며 실행 중인 세션이 없으면 null입니다.")
        Long sessionId,
        @Schema(description = "현재 집중 대상 투두의 가장 최근 작업 세션 ID. 다른 투두의 타이머 요청에 사용하지 않습니다.")
        Long latestSessionId,
        @Schema(description = "현재 실행 중인 세션의 시작 시각(UTC)입니다.")
        Instant sessionStartedAt,
        @Schema(description = "종료된 Session의 저장 초 합계") long closedDurationSeconds,
        @Schema(description = "서버 시각 기준 현재 열린 구간 경과 초") long currentSessionElapsedSeconds,
        @Schema(description = "종료 구간 합계와 실행 중 경과 초의 합") long totalDurationSeconds,
        @Schema(description = "최종 선택 예상시간에서 총 집중시간을 뺀 남은 시간(초). 최소 0입니다.")
        long remainingEstimatedSeconds,
        @Schema(description = "최종 선택 예상시간보다 더 집중한 시간(초). 초과하지 않았으면 0입니다.")
        long estimateOverrunSeconds,
        @Schema(description = "진행 중인 작업 전환 대기 정보. 대기가 없으면 null입니다.")
        SwitchWait pendingSwitch,
        @Schema(description = "서버 현재 시각(UTC). 타이머·대기시간 표시의 기준으로 사용합니다.")
        Instant serverTime
) {
    public enum State { IDLE, RUNNING, PAUSED, SWITCH_WAITING }

    // 대기 정보는 사용자에게 귀속되며 만료·확인 시 다시 검증한다.
    public record SwitchWait(
            @Schema(description = "서버가 발급한 작업 전환 요청 UUID. 확정·취소 API 경로에 그대로 사용합니다.")
            UUID switchId,
            @Schema(description = "전환해서 시작하려는 다른 투두의 ID입니다.")
            Long targetTodoId,
            @Schema(description = "작업 전환을 요청한 시각(UTC)입니다.")
            Instant requestedAt,
            @Schema(description = "작업 전환을 확정할 수 있는 가장 이른 시각(UTC). 이 시각 이후 확정 API를 호출해야 실제 전환됩니다.")
            Instant readyAt,
            @Schema(description = "이 응답에 포함된 임시 초안·제안 또는 대기 요청의 만료 시각(UTC)입니다.")
            Instant expiresAt) { }
}

