package com.swu.atch.recurrence.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.List;
import com.swu.atch.recurrence.domain.*;

@Schema(description = "등록된 반복 일정 규칙. 변경은 앞으로 생성할 회차에만 반영")
public record RecurrenceRuleResponse(
        @Schema(description = "규칙 ID. 규칙 조회·수정·활성·삭제 API 경로에 사용합니다.")
        Long id,

        @Schema(description = "등록 출처인 Pattern ID입니다.")
        Long sourcePatternId,

        @Schema(description = "감지된 반복 태스크명입니다.")
        String taskName,

        @Schema(description = "주기. DAILY=매일, WEEKLY=매주입니다.")
        RecurrenceFrequency frequency,

        @Schema(description = "WEEKLY의 반복 요일. DAILY는 null입니다.")
        DayOfWeek dayOfWeek,

        @Schema(description = "하루종일 반복 여부입니다.")
        boolean allDay,

        @Schema(description = "서울 기준 예정 시작 시각. 하루종일은 null입니다.")
        LocalTime scheduledTime,

        @Schema(description = "생성될 Todo의 기본 메모입니다.")
        String memo,

        @Schema(description = "시작부터 마감까지의 간격(분). 하루종일은 1440으로 고정합니다.")
        Integer defaultDeadlineOffsetMinutes,

        @Schema(description = "기본 예상 작업 소요시간(분)입니다.")
        Integer defaultUserEstimatedDurationMinutes,

        @Schema(description = "이 규칙으로 새 Todo를 생성할지 여부입니다.")
        boolean enabled,

        @Schema(description = "자동 생성 대상으로 인정하는 최초 예정 시작 일시(UTC). 프론트 입력값·정확한 배치 실행 시각이 아닙니다.")
        Instant generationNotBefore,

        @Schema(description = "규칙 등록 시각(UTC)입니다.")
        Instant createdAt,

        @Schema(description = "마지막 수정 시각(UTC). 후속 변경 요청의 expectedUpdatedAt에 복사합니다.")
        Instant updatedAt
) {
}
