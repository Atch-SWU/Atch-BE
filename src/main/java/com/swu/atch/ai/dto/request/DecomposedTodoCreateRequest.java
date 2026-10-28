package com.swu.atch.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.todo.dto.request.EstimateChoiceRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

// 최종 승인한 세부 Todo 일괄 생성. 원본 부모는 생성하지 않음
@Schema(description = "최종 승인한 세부 Todo 일괄 생성. 원본 부모는 생성하지 않음")
public record DecomposedTodoCreateRequest(
        @NotNull @Schema(description = "동일 본문 재전송을 식별하는 생성 요청 ID. 생성 결과와 동일 트랜잭션으로 보존", requiredMode = Schema.RequiredMode.REQUIRED) UUID creationRequestId,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID decompositionDraftId,
        @NotBlank @Size(max = 200) @Schema(description = "원본 작업명. 생성할 모든 세부 Todo에 복사", example = "기획안 초안 작성", requiredMode = Schema.RequiredMode.REQUIRED) String sourceTaskName,
        @Size(max = 2000) String memo,
        @NotNull @Schema(example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate startDate,
        @Schema(example = "18:00:00", type = "string", format = "time") LocalTime startTime,
        @NotNull @Schema(example = "2026-10-29", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate dueDate,
        @Schema(example = "20:00:00", type = "string", format = "time") LocalTime dueTime,
        @NotNull @Schema(example = "false", requiredMode = Schema.RequiredMode.REQUIRED) Boolean allDay,
        @NotNull @Size(min = 1, max = 20) @Valid
        @Schema(description = "확정된 순서. 각 사용자 입력 분 필수, 합계는 서버가 계산", requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotNull DecomposedTaskInput> tasks,
        @Valid @Schema(description = "생략 시 USER. AI 선택은 각 작업의 검증된 서버 제안시간 적용") EstimateChoiceRequest estimateChoice
) {

}

