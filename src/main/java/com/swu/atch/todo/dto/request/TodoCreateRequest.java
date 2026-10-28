package com.swu.atch.todo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

// 일반 Todo 생성 입력. 사용자·상태·AI 예상값·반복 회차는 서버가 결정한다.
// 필드 간 일정 관계와 서울 기준 오늘 검증은 후속 Service에서 처리한다.
@Schema(description = "일반 Todo 생성. 날짜·시각 입력의 시간대는 Asia/Seoul")
public record TodoCreateRequest(
        @NotBlank(message = "작업명은 필수입니다.")
        @Size(max = 200, message = "작업명은 200자 이하여야 합니다.")
        @Schema(description = "작업명. 앞뒤 공백 제거 후 저장", example = "TODO API 설계", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Size(max = 2000, message = "메모는 2000자 이하여야 합니다.")
        @Schema(description = "선택 메모. API 최대 2000자", example = "프론트엔드와 요청 형식 확인")
        String memo,

        @NotNull(message = "시작 날짜는 필수입니다.")
        @Schema(description = "서울 기준 시작 날짜. 신규 생성 시 과거 날짜 불가. 오늘의 지난 시각은 허용", example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate startDate,

        @Schema(description = "서울 기준 시작 시각. allDay=false이면 필수, true이면 null", example = "09:00:00", type = "string", format = "time")
        LocalTime startTime,

        @NotNull(message = "마감 날짜는 필수입니다.")
        @Schema(description = "서울 기준 마감 날짜. 하루종일은 startDate와 같은 날짜 입력", example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate dueDate,

        @Schema(description = "서울 기준 마감 시각. allDay=false이면 필수, true이면 null", example = "10:00:00", type = "string", format = "time")
        LocalTime dueTime,

        @NotNull(message = "하루종일 여부는 필수입니다.")
        @Schema(description = "true: 같은 날짜의 00:00부터 다음 날 00:00 미만. 시각 입력은 모두 null", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean allDay,

        @NotNull(message = "사용자 예상시간은 필수입니다.")
        @Positive(message = "사용자 예상시간은 0보다 커야 합니다.")
        @Schema(description = "사용자 예상시간(분). USER_INPUT·USER로 생성하며 임의의 AI 예상값은 받지 않음", example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer userEstimatedDurationMinutes
) {
}
