package com.swu.atch.recurrence.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.List;
import com.swu.atch.recurrence.domain.*;

@Schema(description = "추천 선택 처리 결과")
public record RecommendationActionResponse(
        @Schema(description = "선택한 패턴 ID입니다.")
        Long patternId,

        @Schema(description = "DETECTING=초기화 후 관측 중, DISMISSED=다시 제안하지 않기, SUGGESTED=추천, REGISTERED=규칙 등록입니다.")
        RepeatPatternStatus status,

        @Schema(description = "현재 관측 횟수. 이번만 할게요 직후에는 0입니다.")
        int occurrenceCount,

        @Schema(description = "마지막 수정 시각(UTC). 후속 변경 요청의 expectedUpdatedAt에 복사합니다.")
        Instant updatedAt
) {
}
