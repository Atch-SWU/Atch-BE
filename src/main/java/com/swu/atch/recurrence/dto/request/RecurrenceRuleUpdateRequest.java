package com.swu.atch.recurrence.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
import com.swu.atch.recurrence.domain.*;

@Schema(description = "규칙 편집 폼 전체 수정. 주기는 등록 후 변경할 수 없습니다")
public record RecurrenceRuleUpdateRequest(
        @NotBlank @Size(max = 200)
        @Schema(description = "규칙명 겸 생성될 Todo의 태스크명. 앞뒤 공백 제거 후 1~200자입니다.")
        String taskName,

        @Size(max = 2000)
        @Schema(description = "반복 Todo의 기본 메모(선택, 최대 2000자). 전체 수정에서 null이면 메모를 삭제합니다.")
        String memo,

        @Schema(description = "반복 요일. WEEKLY는 MONDAY~SUNDAY 중 하나가 필수이며 DAILY는 null입니다.")
        DayOfWeek dayOfWeek,

        @NotNull
        @Schema(description = "하루종일 반복 여부(필수). true이면 scheduledTime=null, 마감 간격은 하루로 고정합니다.")
        Boolean allDay,

        @Schema(description = "서울 기준 예정 시작 시각. 예: 09:00:00. allDay=false이면 필수, true이면 null입니다.")
        LocalTime scheduledTime,

        @Positive
        @Schema(description = "시작부터 마감까지의 간격(분). 시각 지정은 필수·양수, 하루종일은 생략 또는 1440이며 서버가 1440으로 고정합니다.")
        Integer defaultDeadlineOffsetMinutes,

        @NotNull @Positive
        @Schema(description = "새 반복 Todo에 복사할 기본 예상 작업 소요시간(분). 0보다 큰 정수이며 AI 제안시간과 구별합니다.")
        Integer defaultUserEstimatedDurationMinutes,

        @NotNull
        @Schema(description = "직전 추천/규칙 응답의 updatedAt을 그대로 복사합니다. 사용자가 입력하는 값이 아닙니다. 오래된 화면은 409입니다.")
        Instant expectedUpdatedAt
) {
}
