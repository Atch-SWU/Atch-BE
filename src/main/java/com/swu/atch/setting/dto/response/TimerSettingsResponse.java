package com.swu.atch.setting.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

// 반복 추천 설정과 분리한 타이머 전환 설정
@Schema(description = "반복 추천 설정과 분리한 타이머 전환 설정")
public record TimerSettingsResponse(
        @Schema(description = "작업 전환 제한 사용 여부. true이면 다른 작업으로 전환할 때 설정된 시간만큼 기다립니다. 변경 요청에서 null이면 기존 설정을 유지합니다.")
        boolean taskSwitchRestrictionEnabled,
        @Schema(description = "작업 전환 대기 시간(분). 0보다 큰 정수입니다. 변경 요청에서 null이면 기존 설정을 유지하며 이미 시작한 대기에는 소급 적용하지 않습니다.")
        int taskSwitchWaitMinutes,
        @Schema(description = "마지막 저장·수정 시각(UTC)입니다.")
        Instant updatedAt
) {

}

