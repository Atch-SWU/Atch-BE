package com.swu.atch.recurrence.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.List;
import com.swu.atch.recurrence.domain.*;

@Schema(description = "반복 추천 전체 설정")
public record RecurrenceSettingsResponse(
        @Schema(description = "감지·추천 사용 여부. 개별 규칙 활성 여부와 독립입니다.")
        boolean repeatRecommendationEnabled,

        @Schema(description = "마지막 수정 시각(UTC). 후속 변경 요청의 expectedUpdatedAt에 복사합니다.")
        Instant updatedAt
) {
}
