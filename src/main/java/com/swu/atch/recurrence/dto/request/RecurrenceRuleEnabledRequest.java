package com.swu.atch.recurrence.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
import com.swu.atch.recurrence.domain.*;

@Schema(description = "개별 규칙의 자동 생성 여부 변경")
public record RecurrenceRuleEnabledRequest(
        @NotNull
        @Schema(description = "이 규칙으로 새 Todo를 생성할지 여부. 기존 Todo는 유지합니다.")
        Boolean enabled,

        @NotNull
        @Schema(description = "직전 추천/규칙 응답의 updatedAt을 그대로 복사합니다. 사용자가 입력하는 값이 아닙니다. 오래된 화면은 409입니다.")
        Instant expectedUpdatedAt
) {
}
