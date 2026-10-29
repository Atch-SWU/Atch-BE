package com.swu.atch.recurrence.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.List;
import com.swu.atch.recurrence.domain.*;

@Schema(description = "설정이 꺼져 있으면 enabled=false와 빈 목록을 반환")
public record RecurrenceRecommendationPageResponse(
        @Schema(description = "전체 반복 추천 기능 사용 여부입니다. false이면 items는 빈 목록이며 추천 안내를 표시하지 않습니다. 등록된 규칙의 자동 생성 활성 여부와는 별개입니다.")
        boolean enabled,

        @Schema(description = "본인의 현재 추천 목록입니다. 방금 저장한 투두에 한정되지 않습니다. 비어 있으면 추천 안내를 표시하지 않으며, 항목은 patternId로 식별합니다.")
        List<RecurrenceRecommendationResponse> items,

        @Schema(description = "현재 페이지 번호. 0이 첫 페이지입니다.")
        int page,

        @Schema(description = "적용된 페이지당 최대 항목 수입니다.")
        int size,

        @Schema(description = "조건에 맞는 전체 항목 수입니다.")
        long totalElements,

        @Schema(description = "전체 페이지 수입니다.")
        int totalPages,

        @Schema(description = "다음 페이지 존재 여부입니다.")
        boolean hasNext,

        @Schema(description = "이번 감지 기준 시각(UTC)입니다.")
        Instant asOf
) {
}
