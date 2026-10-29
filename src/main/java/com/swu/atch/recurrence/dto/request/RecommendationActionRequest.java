package com.swu.atch.recurrence.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
import com.swu.atch.recurrence.domain.*;

@Schema(description = "이번만 할게요 / 다시 제안하지 않기 요청")
public record RecommendationActionRequest(
        @NotNull
        @Schema(description = "직전 추천/규칙 응답의 updatedAt을 그대로 복사합니다. 사용자가 입력하는 값이 아닙니다. 오래된 화면은 409입니다.")
        Instant expectedUpdatedAt
) {
}
