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
        @Schema(description = "태스크명. 사용자가 입력하는 투두 이름(최대 200자).", example = "TODO API 구현", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Size(max = 2000, message = "메모는 2000자 이하여야 합니다.")
        @Schema(description = "메모(선택). 작업에 참고할 내용을 입력합니다(최대 2000자).", example = "요청·응답 계약 검토")
        String memo,
        @NotNull(message = "시작 날짜는 필수입니다.")
        @Schema(description = "수정할 시작 날짜(서울 기준 YYYY-MM-DD). 최초 날짜를 실제로 변경하면 배정 날짜도 다시 계산합니다. 과거 날짜를 입력해도 현재 배정 날짜를 오늘 이전으로 되돌리지 않습니다.", example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate startDate,
        @Schema(description = "시작 시각. 서울 기준 HH:mm:ss 형식입니다. allDay=false이면 필수이며 true이면 null을 보냅니다.", example = "09:00:00", type = "string", format = "time")
        LocalTime startTime,
        @NotNull(message = "마감 날짜는 필수입니다.")
        @Schema(description = "마감 날짜. YYYY-MM-DD 형식입니다. 하루종일 일정은 시작 날짜와 같은 날짜를 보냅니다.", example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate dueDate,
        @Schema(description = "마감 시각. 서울 기준 HH:mm:ss 형식입니다. allDay=false이면 필수이며 마감 일시는 시작 일시보다 뒤여야 합니다. true이면 null을 보냅니다.", example = "11:00:00", type = "string", format = "time")
        LocalTime dueTime,
        @NotNull(message = "하루종일 여부는 필수입니다.")
        @Schema(description = "하루종일 일정 여부. true이면 시작·마감 날짜를 같은 날로 지정하고 두 시각은 null로 보냅니다. 서버가 해당 서울 날짜 하루로 처리합니다.", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean allDay,
        @NotNull(message = "사용자 예상시간은 필수입니다.")
        @Positive(message = "사용자 예상시간은 0보다 커야 합니다.")
        @Schema(description = "사용자가 직접 입력한 예상 작업 소요시간(분). 0보다 큰 정수입니다. AI 제안시간으로 덮어쓰지 않습니다.", example = "45", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer userEstimatedDurationMinutes,
        @Schema(description = "예상시간 선택 정보(선택). 생략하면 직접 입력시간(USER)을 사용합니다. AI 초안을 연결해 저장하거나 제안을 수락·거절할 때 draftId와 선택 정보를 보냅니다.")
        @jakarta.validation.Valid EstimateChoiceRequest estimateChoice
) {
    public TodoUpdateRequest(String title, String memo, LocalDate startDate, LocalTime startTime,
            LocalDate dueDate, LocalTime dueTime, Boolean allDay, Integer userEstimatedDurationMinutes) {
        this(title, memo, startDate, startTime, dueDate, dueTime, allDay, userEstimatedDurationMinutes, null);
    }
}
