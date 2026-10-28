package com.swu.atch.setting.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

// 타이머 설정 부분 변경. null은 유지이며 적어도 한 항목 필요
@Schema(description = "타이머 설정 부분 변경. null은 유지이며 적어도 한 항목 필요")
public record TimerSettingsUpdateRequest(
        @Schema(description = "작업 전환 제한 사용 여부. true이면 다른 작업으로 전환할 때 설정된 시간만큼 기다립니다. 변경 요청에서 null이면 기존 설정을 유지합니다.") Boolean taskSwitchRestrictionEnabled,
        @Positive @Schema(description = "작업 전환 대기 시간(분). 0보다 큰 정수입니다. 변경 요청에서 null이면 기존 설정을 유지하며 이미 시작한 대기에는 소급 적용하지 않습니다.", example = "10") Integer taskSwitchWaitMinutes
) {

}

