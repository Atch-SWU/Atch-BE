package com.swu.atch.session.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// 일시정지 여부와 관계없이 미완료 집중 대상에서 다른 Todo로 전환
@Schema(description = "일시정지 여부와 관계없이 미완료 집중 대상에서 다른 Todo로 전환")
public record TimerSwitchRequest(
        @NotNull @Positive @Schema(description = "현재 집중 대상 Todo ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long sourceTodoId,
        @NotNull @Positive @Schema(description = "전환할 본인 활성 Todo ID", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        Long targetTodoId,
        @NotNull @Positive @Schema(description = "실행 중 또는 마지막 종료 구간 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long sourceSessionId
) {

}

