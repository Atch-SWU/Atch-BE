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

// 날짜·상태를 먼저 필터링한 뒤 페이지를 나눈다. 조합 검증은 Service 책임이다.
public record TodoListQuery(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "현재 배정 날짜로 하루를 조회합니다(YYYY-MM-DD). from/to와 함께 보낼 수 없고 날짜 조건을 모두 생략하면 서울 기준 오늘입니다.", example = "2026-10-29")
        LocalDate date,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "조회 시작 날짜(포함). 서울 기준 YYYY-MM-DD 형식이며 to와 함께 보냅니다.", example = "2026-10-26")
        LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "조회 끝 날짜(포함). 서울 기준 YYYY-MM-DD 형식이며 from 이상이어야 합니다.", example = "2026-11-01")
        LocalDate to,
        @Parameter(description = "진행 상태 필터(선택). PENDING=진행 전, IN_PROGRESS=진행 중, PAUSED=일시정지, COMPLETED=완료. 복수 상태는 status=PENDING,PAUSED처럼 쉼표로 연결하며 생략하면 전체입니다.",
                example = "PENDING,PAUSED", style = ParameterStyle.FORM, explode = Explode.FALSE,
                array = @ArraySchema(schema = @Schema(implementation = TodoStatus.class)))
        List<TodoStatus> status,
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        @Parameter(description = "페이지 번호. 0이 첫 페이지입니다. 생략하면 0을 사용합니다.", schema = @Schema(defaultValue = "0", minimum = "0"))
        Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
        @Parameter(description = "한 페이지의 최대 항목 수. 기본 20, 최대 100입니다. 마지막 페이지는 이보다 적을 수 있습니다.", schema = @Schema(defaultValue = "20", minimum = "1", maximum = "100"))
        Integer size
) {
    public TodoListQuery {
        // 입력 생략에만 기본값을 적용하고 잘못된 음수·0은 검증 오류로 남긴다.
        status = status == null ? List.of() : List.copyOf(status);
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
