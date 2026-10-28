package com.swu.atch.report.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

// 서울 기준 양 끝 포함 조회 기간. 순서·최근 3년 검증은 Service
@Schema(description = "서울 기준 양 끝 포함 조회 기간. 순서·최근 3년 검증은 Service")
public record ReportRangeQuery(
        @NotNull @Schema(example = "2026-10-01", requiredMode = Schema.RequiredMode.REQUIRED, description = "조회 시작 날짜(포함). 서울 기준 YYYY-MM-DD 형식이며 to와 함께 보냅니다.") LocalDate from,
        @NotNull @Schema(example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED, description = "조회 끝 날짜(포함). 서울 기준 YYYY-MM-DD 형식이며 from 이상이어야 합니다.") LocalDate to
) {

}

