package com.swu.atch.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

// 세부 작업별 사용자 시간을 보정. 각 작업의 성공 사용 이력과 입력 일치를 따로 확인
@Schema(description = "세부 작업별 사용자 시간을 보정. 각 작업의 성공 사용 이력과 입력 일치를 따로 확인")
public record TimePredictionBatchRequest(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID decompositionDraftId,
        @NotBlank @Size(max = 200) @Schema(example = "기획안 초안 작성", requiredMode = Schema.RequiredMode.REQUIRED) String sourceTaskName,
        @Size(max = 2000) String memo,
        @NotNull @Size(min = 1, max = 20) @Valid
        @Schema(description = "최대 20개. 모든 사용자 예상시간 입력 후 요청하며 중복 draftId 불가", requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotNull DecomposedTaskInput> tasks
) {

}

