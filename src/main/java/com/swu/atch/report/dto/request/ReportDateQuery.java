package com.swu.atch.report.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

// 일간 날짜 또는 주간에 포함된 날짜. 생략 시 서울 오늘, 최근 3년 범위는 Service에서 확인
@Schema(description = "일간 날짜 또는 주간에 포함된 날짜. 생략 시 서울 오늘, 최근 3년 범위는 Service에서 확인")
public record ReportDateQuery(
        @Schema(description = "서울 기준 날짜. 주간 조회는 해당 날짜가 속한 월요일 시작 주", example = "2026-10-29") LocalDate date
) {

}

