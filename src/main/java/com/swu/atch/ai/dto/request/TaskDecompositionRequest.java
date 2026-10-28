package com.swu.atch.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

// 선택적인 짧은 작업 내용과 일정을 이용한 분해. 시간 초깃값은 제안하지 않음
@Schema(description = "선택적인 짧은 작업 내용과 일정을 이용한 분해. 시간 초깃값은 제안하지 않음")
public record TaskDecompositionRequest(
        @NotNull @Schema(description = "AI 작업 초안 식별자. /api/v1/ai/drafts 또는 분해 결과에서 받은 UUID를 그대로 보냅니다. 사용자가 입력하거나 프론트가 임의로 생성하는 값이 아닙니다.", requiredMode = Schema.RequiredMode.REQUIRED) UUID draftId,
        @NotBlank @Size(max = 200) @Schema(example = "기획안 초안 작성", requiredMode = Schema.RequiredMode.REQUIRED, description = "태스크명. 사용자가 입력하는 투두 이름(최대 200자).") String title,
        @Size(max = 200) @Pattern(regexp = "[^\\r\\n\\u0085\\u2028\\u2029]*", message = "작업 내용은 줄바꿈 없는 한 줄이어야 합니다.")
        @Schema(description = "작업 내용(선택). 무엇을 해야 하는지 짧은 한 줄로 설명합니다(최대 200자). 예: 요구사항을 읽고 발표 자료 초안을 만든다.", example = "발표에서 문제와 해결 방향을 설명할 기획안") String taskDescription,
        @NotNull @Schema(example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED, description = "시작 날짜. 서울 기준 YYYY-MM-DD 형식입니다. 신규 생성은 오늘 이후 날짜를 입력합니다.") LocalDate startDate,
        @Schema(description = "시작 시각. 서울 기준 HH:mm:ss 형식입니다. allDay=false이면 필수이며 true이면 null을 보냅니다.", example = "18:00:00", type = "string", format = "time") LocalTime startTime,
        @NotNull @Schema(example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED, description = "마감 날짜. YYYY-MM-DD 형식입니다. 하루종일 일정은 시작 날짜와 같은 날짜를 보냅니다.") LocalDate dueDate,
        @Schema(description = "마감 시각. 서울 기준 HH:mm:ss 형식입니다. allDay=false이면 필수이며 마감 일시는 시작 일시보다 뒤여야 합니다. true이면 null을 보냅니다.", example = "20:00:00", type = "string", format = "time") LocalTime dueTime,
        @NotNull @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED, description = "하루종일 일정 여부. true이면 시작·마감 날짜를 같은 날로 지정하고 두 시각은 null로 보냅니다. 서버가 해당 서울 날짜 하루로 처리합니다.") Boolean allDay,
        @Schema(description = "메모(선택). 작업에 참고할 내용을 입력합니다(최대 2000자).")
        @Size(max = 2000) String memo
) {

}

