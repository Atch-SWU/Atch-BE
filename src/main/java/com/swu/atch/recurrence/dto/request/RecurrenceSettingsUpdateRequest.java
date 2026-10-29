package com.swu.atch.recurrence.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
import com.swu.atch.recurrence.domain.*;

@Schema(description = "전체 반복 추천 설정 변경")
public record RecurrenceSettingsUpdateRequest(
        @NotNull
        @Schema(description = "패턴 감지·추천을 켜거나 끕니다. 이미 등록된 개별 Rule.enabled에는 영향을 주지 않습니다.")
        Boolean repeatRecommendationEnabled
) {
}
