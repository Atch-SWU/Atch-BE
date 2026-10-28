package com.swu.atch.todo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

// 수정 가능한 입력 전체를 교체한다. memo=null은 메모 삭제를 의미한다.
// 신규 생성의 과거 날짜 제한을 기존 일정 수정에 그대로 적용하지 않는다.
@Schema(description = "Todo 편집 폼 전체 교체. 실행 중에는 일시정지, 완료 상태에서는 완료 취소 후 수정")
public record TodoUpdateRequest(
        @NotBlank(message = "작업명은 필수입니다.")
        @Size(max = 200, message = "작업명은 200자 이하여야 합니다.")
        @Schema(description = "작업명. 앞뒤 공백 제거 후 저장", example = "TODO API 구현", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Size(max = 2000, message = "메모는 2000자 이하여야 합니다.")
        @Schema(description = "최대 2000자. null이면 기존 메모 삭제", example = "요청·응답 계약 검토")
        String memo,

        @NotNull(message = "시작 날짜는 필수입니다.")
        @Schema(description = "서울 기준 시작 날짜. 변경할 때만 현재 배정 날짜도 이 날짜로 재지정", example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate startDate,

        @Schema(description = "서울 기준 시작 시각. allDay=false이면 필수, true이면 null", example = "09:00:00", type = "string", format = "time")
        LocalTime startTime,

        @NotNull(message = "마감 날짜는 필수입니다.")
        @Schema(description = "서울 기준 마감 날짜. 하루종일은 startDate와 같은 날짜 입력", example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate dueDate,

        @Schema(description = "서울 기준 마감 시각. allDay=false이면 필수, true이면 null", example = "11:00:00", type = "string", format = "time")
        LocalTime dueTime,

        @NotNull(message = "하루종일 여부는 필수입니다.")
        @Schema(description = "true: 서울 해당 날짜 00:00부터 다음 날 00:00 미만. 시각은 null", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean allDay,

        @NotNull(message = "사용자 예상시간은 필수입니다.")
        @Positive(message = "사용자 예상시간은 0보다 커야 합니다.")
        @Schema(description = "예상시간(분). 반복 기본값을 직접 변경하면 USER_INPUT으로 전환", example = "45", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer userEstimatedDurationMinutes
) {
}
