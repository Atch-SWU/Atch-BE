package com.swu.atch.todo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

// Spring Page 내부 구조를 노출하지 않는 목록 계약
public record TodoPageResponse(
        @Schema(description = "실제 조회 범위의 시작 날짜(서울 기준, 포함)입니다.") LocalDate from,
        @Schema(description = "실제 조회 범위의 끝 날짜(서울 기준, 포함)입니다.") LocalDate to,
        @Schema(description = "날짜·상태 필터 후 currentScheduleDate,startAt,id 오름차순 목록") List<TodoResponse> items,
        @Schema(description = "현재 페이지 번호. 0이 첫 페이지입니다.", example = "0") int page,
        @Schema(description = "요청에 적용된 페이지당 최대 항목 수입니다.", example = "20") int size,
        @Schema(description = "필터에 해당하는 전체 Todo 수", example = "27") long totalElements,
        @Schema(description = "전체 페이지 수. 결과가 없으면 0", example = "2") int totalPages,
        @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext
) {
    public TodoPageResponse {
        items = List.copyOf(items);
    }
}
