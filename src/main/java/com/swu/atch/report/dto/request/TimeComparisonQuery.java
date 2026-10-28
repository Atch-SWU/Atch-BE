package com.swu.atch.report.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.LocalDate;

// 조회 시점에 유효한 완료 Todo의 예상·실제 시간 비교
@Schema(description = "조회 시점에 유효한 완료 Todo의 예상·실제 시간 비교")
public record TimeComparisonQuery(
        @NotNull @Schema(example = "2026-10-01", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate from,
        @NotNull @Schema(example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate to,
        @Min(0) @Schema(defaultValue = "0") Integer page,
        @Min(1) @Max(100) @Schema(defaultValue = "20") Integer size
) {
    public TimeComparisonQuery {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}

