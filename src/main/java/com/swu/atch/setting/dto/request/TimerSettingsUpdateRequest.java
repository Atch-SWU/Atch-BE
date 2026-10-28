package com.swu.atch.setting.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

// 타이머 설정 부분 변경. null은 유지이며 적어도 한 항목 필요
@Schema(description = "타이머 설정 부분 변경. null은 유지이며 적어도 한 항목 필요")
public record TimerSettingsUpdateRequest(
        @Schema(description = "작업 전환 제한 활성화 여부. null은 현재 값 유지") Boolean taskSwitchRestrictionEnabled,
        @Positive @Schema(description = "양수인 대기 분. null은 현재 값 유지", example = "10") Integer taskSwitchWaitMinutes
) {

}

