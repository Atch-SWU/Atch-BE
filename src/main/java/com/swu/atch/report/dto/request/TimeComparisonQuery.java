package com.swu.atch.report.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.LocalDate;

// 조회 시점에 유효한 완료 Todo의 예상·실제 시간 비교
@Schema(description = "조회 시점에 유효한 완료 Todo의 예상·실제 시간 비교")
public record TimeComparisonQuery(
        @NotNull @Schema(example = "2026-10-01", requiredMode = Schema.RequiredMode.REQUIRED, description = "조회 시작 날짜(포함). 서울 기준 YYYY-MM-DD 형식이며 to와 함께 보냅니다.") LocalDate from,
        @NotNull @Schema(example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED, description = "조회 끝 날짜(포함). 서울 기준 YYYY-MM-DD 형식이며 from 이상이어야 합니다.") LocalDate to,
        @Min(0) @Schema(defaultValue = "0", description = "페이지 번호. 0이 첫 페이지입니다. 생략하면 0을 사용합니다.") Integer page,
        @Min(1) @Max(100) @Schema(defaultValue = "20", description = "한 페이지의 최대 항목 수. 기본 20, 최대 100입니다. 마지막 페이지는 이보다 적을 수 있습니다.") Integer size
) {
    public TimeComparisonQuery {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}

