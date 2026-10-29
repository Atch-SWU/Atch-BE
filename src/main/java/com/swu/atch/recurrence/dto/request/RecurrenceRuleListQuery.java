package com.swu.atch.recurrence.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
import com.swu.atch.recurrence.domain.*;

@Schema(description = "규칙 목록 조회 조건")
public record RecurrenceRuleListQuery(
        @Schema(description = "활성 여부 필터(선택). 생략하면 활성·비활성 규칙을 모두 조회합니다.")
        Boolean enabled,

        @Min(0)
        @Schema(description = "페이지 번호. 0이 첫 페이지이며 생략하면 0입니다.")
        Integer page,

        @Min(1) @Max(100)
        @Schema(description = "페이지당 최대 항목 수. 기본 20, 1~100입니다.")
        Integer size
) {
    public RecurrenceRuleListQuery {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
