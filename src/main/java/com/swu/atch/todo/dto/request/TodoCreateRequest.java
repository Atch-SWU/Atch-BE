package com.swu.atch.todo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

// 일반 Todo 생성 입력. 사용자·상태·AI 예상값·반복 회차는 서버가 결정한다.
// 필드 간 일정 관계와 서울 기준 오늘 검증은 Service에서 처리한다.
@Schema(description = "일반 Todo 생성. 날짜·시각 입력의 시간대는 Asia/Seoul")
public record TodoCreateRequest(
        @NotBlank(message = "작업명은 필수입니다.")
        @Size(max = 200, message = "작업명은 200자 이하여야 합니다.")
        @Schema(description = "태스크명. 사용자가 입력하는 투두 이름(최대 200자).", example = "TODO API 설계", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Size(max = 2000, message = "메모는 2000자 이하여야 합니다.")
        @Schema(description = "메모(선택). 작업에 참고할 내용을 입력합니다(최대 2000자).", example = "프론트엔드와 요청 형식 확인")
        String memo,
        @NotNull(message = "시작 날짜는 필수입니다.")
        @Schema(description = "시작 날짜. 서울 기준 YYYY-MM-DD 형식입니다. 신규 생성은 오늘 이후 날짜를 입력합니다.", example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate startDate,
        @Schema(description = "시작 시각. 서울 기준 HH:mm:ss 형식입니다. allDay=false이면 필수이며 true이면 null을 보냅니다.", example = "09:00:00", type = "string", format = "time")
        LocalTime startTime,
        @NotNull(message = "마감 날짜는 필수입니다.")
        @Schema(description = "마감 날짜. YYYY-MM-DD 형식입니다. 하루종일 일정은 시작 날짜와 같은 날짜를 보냅니다.", example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate dueDate,
        @Schema(description = "마감 시각. 서울 기준 HH:mm:ss 형식입니다. allDay=false이면 필수이며 마감 일시는 시작 일시보다 뒤여야 합니다. true이면 null을 보냅니다.", example = "10:00:00", type = "string", format = "time")
        LocalTime dueTime,
        @NotNull(message = "하루종일 여부는 필수입니다.")
        @Schema(description = "하루종일 일정 여부. true이면 시작·마감 날짜를 같은 날로 지정하고 두 시각은 null로 보냅니다. 서버가 해당 서울 날짜 하루로 처리합니다.", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean allDay,
        @NotNull(message = "사용자 예상시간은 필수입니다.")
        @Positive(message = "사용자 예상시간은 0보다 커야 합니다.")
        @Schema(description = "사용자가 직접 입력한 예상 작업 소요시간(분). 0보다 큰 정수입니다. AI 제안시간으로 덮어쓰지 않습니다.", example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer userEstimatedDurationMinutes,
        @Schema(description = "예상시간 선택 정보(선택). 생략하면 직접 입력시간(USER)을 사용합니다. AI 초안을 연결해 저장하거나 제안을 수락·거절할 때 draftId와 선택 정보를 보냅니다.")
        @jakarta.validation.Valid EstimateChoiceRequest estimateChoice,
        @Schema(description = "중복 저장 방지용 UUID. estimateChoice를 보내면 필수이며 프론트가 한 저장 동작당 생성합니다. 같은 본문 재전송에는 같은 UUID를 유지합니다. 일반 생성에서 단독으로 보내도 중복 방지를 적용하지 않습니다.")
        java.util.UUID creationRequestId
) {
    public TodoCreateRequest(String title, String memo, LocalDate startDate, LocalTime startTime,
            LocalDate dueDate, LocalTime dueTime, Boolean allDay, Integer userEstimatedDurationMinutes) {
        this(title, memo, startDate, startTime, dueDate, dueTime, allDay, userEstimatedDurationMinutes, null, null);
    }
}
