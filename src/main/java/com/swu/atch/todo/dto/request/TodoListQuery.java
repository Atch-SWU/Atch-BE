package com.swu.atch.todo.dto.request;

import com.swu.atch.todo.domain.TodoStatus;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

// 날짜·상태를 먼저 필터링한 뒤 페이지를 나눈다. 조합 검증은 후속 Service 책임이다.
public record TodoListQuery(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "현재 배정 날짜. from/to와 동시 사용 불가. 날짜 조건 생략 시 서울 오늘", example = "2026-10-29")
        LocalDate date,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "배정 날짜 범위 시작(포함). to와 함께 입력", example = "2026-10-26")
        LocalDate from,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "배정 날짜 범위 끝(포함). from과 함께 입력", example = "2026-11-01")
        LocalDate to,

        @Parameter(description = "복수 상태를 쉼표로 연결. 생략 시 전체 상태. 미완료는 PENDING,IN_PROGRESS,PAUSED",
                example = "PENDING,PAUSED", style = ParameterStyle.FORM, explode = Explode.FALSE,
                array = @ArraySchema(schema = @Schema(implementation = TodoStatus.class)))
        List<TodoStatus> status,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        @Parameter(description = "0부터 시작하는 페이지 번호. 0은 첫 묶음, 1은 다음 묶음", schema = @Schema(defaultValue = "0", minimum = "0"))
        Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
        @Parameter(description = "한 페이지의 최대 Todo 수. 마지막 페이지는 더 적을 수 있음", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
        Integer size
) {
    public TodoListQuery {
        // 입력 생략에만 기본값을 적용하고 잘못된 음수·0은 검증 오류로 남긴다.
        status = status == null ? List.of() : List.copyOf(status);
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
