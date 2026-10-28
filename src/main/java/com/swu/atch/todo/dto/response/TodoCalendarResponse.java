package com.swu.atch.todo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

// 시작~마감의 겹침이나 과거 확정 통계가 아닌 현재 배정 날짜의 존재 표시
public record TodoCalendarResponse(
        @Schema(description = "실제 조회 범위의 시작 날짜(서울 기준, 포함)입니다.") LocalDate from,
        @Schema(description = "실제 조회 범위의 끝 날짜(서울 기준, 포함)입니다.") LocalDate to,
        @Schema(description = "본인 활성 Todo가 있는 배정 날짜. 완료 포함, 삭제 제외, 중복 제거·오름차순") List<LocalDate> dates
) {
    public TodoCalendarResponse {
        dates = List.copyOf(dates);
    }
}
