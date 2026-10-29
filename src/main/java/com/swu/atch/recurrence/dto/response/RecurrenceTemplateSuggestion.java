package com.swu.atch.recurrence.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.List;
import com.swu.atch.recurrence.domain.*;

@Schema(description = "등록 폼의 기본값 제안. 사용자가 확인해 등록 요청에 보냅니다")
public record RecurrenceTemplateSuggestion(
        @Schema(description = "기본값의 근거 Todo ID. 등록 요청에 다시 보낼 값은 아닙니다.")
        Long sourceTodoId,

        @Schema(description = "최근 유효 근거 Todo의 메모입니다.")
        String memo,

        @Schema(description = "시작부터 마감까지 간격(분). 표현 가능한 정수 범위를 넘으면 null이므로 직접 입력합니다. 하루종일은 1440입니다.")
        Integer defaultDeadlineOffsetMinutes,

        @Schema(description = "최근 근거 Todo의 사용자 예상 작업 소요시간(분)입니다.")
        Integer defaultUserEstimatedDurationMinutes
) {
}
