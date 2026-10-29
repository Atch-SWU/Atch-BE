package com.swu.atch.recurrence.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.List;
import com.swu.atch.recurrence.domain.*;

@Schema(description = "본인 규칙 목록")
public record RecurrenceRulePageResponse(
        @Schema(description = "삭제되지 않은 규칙 목록입니다.")
        List<RecurrenceRuleResponse> items,

        @Schema(description = "현재 페이지 번호. 0이 첫 페이지입니다.")
        int page,

        @Schema(description = "적용된 페이지당 최대 항목 수입니다.")
        int size,

        @Schema(description = "조건에 맞는 전체 항목 수입니다.")
        long totalElements,

        @Schema(description = "전체 페이지 수입니다.")
        int totalPages,

        @Schema(description = "다음 페이지 존재 여부입니다.")
        boolean hasNext
) {
}
