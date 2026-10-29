package com.swu.atch.recurrence.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
import com.swu.atch.recurrence.domain.*;

@Schema(description = "추천을 반복 일정 규칙으로 등록. 태스크명·주기·하루종일 여부·요일·시각은 서버 Pattern에서 복사")
public record RecurrenceRuleRegisterRequest(
        @Size(max = 2000)
        @Schema(description = "반복 Todo의 기본 메모(선택, 최대 2000자). 전체 수정에서 null이면 메모를 삭제합니다.")
        String memo,

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
