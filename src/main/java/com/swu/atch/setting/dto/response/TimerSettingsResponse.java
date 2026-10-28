package com.swu.atch.setting.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

// 반복 추천 설정과 분리한 타이머 전환 설정
@Schema(description = "반복 추천 설정과 분리한 타이머 전환 설정")
public record TimerSettingsResponse(
        boolean taskSwitchRestrictionEnabled, int taskSwitchWaitMinutes, Instant updatedAt
) {

}

