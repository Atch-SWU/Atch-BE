package com.swu.atch.report.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

// 서울 기준 양 끝 포함 조회 기간. 순서·최근 3년 검증은 후속 Service
@Schema(description = "서울 기준 양 끝 포함 조회 기간. 순서·최근 3년 검증은 후속 Service")
public record ReportRangeQuery(
        @NotNull @Schema(example = "2026-10-01", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate from,
        @NotNull @Schema(example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate to
) {

}

