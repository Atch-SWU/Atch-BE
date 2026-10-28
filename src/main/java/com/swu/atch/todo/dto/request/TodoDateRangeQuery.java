package com.swu.atch.todo.dto.request;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

// 캘린더의 표시 기간. from<=to 관계는 후속 Service에서 검증한다.
public record TodoDateRangeQuery(
        @NotNull(message = "조회 시작 날짜는 필수입니다.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "현재 배정 날짜 범위 시작(포함)", example = "2026-10-01", required = true)
        LocalDate from,

        @NotNull(message = "조회 종료 날짜는 필수입니다.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "현재 배정 날짜 범위 끝(포함)", example = "2026-10-31", required = true)
        LocalDate to
) {
}
