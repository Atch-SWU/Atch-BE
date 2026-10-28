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
        @NotNull @Schema(description = "서버 발급 원본 작업 초안 ID. 성공 분해는 이 초안에서 1회", requiredMode = Schema.RequiredMode.REQUIRED) UUID draftId,
        @NotBlank @Size(max = 200) @Schema(example = "기획안 초안 작성", requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Size(max = 200) @Pattern(regexp = "[^\\r\\n\\u0085\\u2028\\u2029]*", message = "작업 내용은 줄바꿈 없는 한 줄이어야 합니다.")
        @Schema(description = "AI가 분해하기 쉽도록 선택적으로 제공하는 짧은 작업 내용. 분량 전용 필드가 아니며 최대 200자 한 줄", example = "발표에서 문제와 해결 방향을 설명할 기획안") String taskDescription,
        @NotNull @Schema(example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate startDate,
        @Schema(description = "allDay=false이면 필수, true이면 null", example = "18:00:00", type = "string", format = "time") LocalTime startTime,
        @NotNull @Schema(example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate dueDate,
        @Schema(description = "allDay=false이면 필수, true이면 null", example = "20:00:00", type = "string", format = "time") LocalTime dueTime,
        @NotNull @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED) Boolean allDay,
        @Size(max = 2000) String memo
) {

}

