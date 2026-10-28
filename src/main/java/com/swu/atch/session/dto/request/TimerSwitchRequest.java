package com.swu.atch.session.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 일시정지 여부와 관계없이 미완료 집중 대상에서 다른 Todo로 전환
@Schema(description = "일시정지 여부와 관계없이 미완료 집중 대상에서 다른 Todo로 전환")
public record TimerSwitchRequest(
        @NotNull @Positive @Schema(description = "전환 전 집중 중인 투두 ID. 현재 타이머 조회 응답의 focusTodoId를 보냅니다.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long sourceTodoId,
        @NotNull @Positive @Schema(description = "전환해서 시작하려는 다른 투두의 ID입니다.", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        Long targetTodoId,
        @NotNull @Positive @Schema(description = "전환 전 투두의 기준 작업 세션 ID. 실행 중이면 현재 세션, 일시정지 상태이면 마지막 종료 세션의 ID를 보냅니다.", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long sourceSessionId
) {

}

