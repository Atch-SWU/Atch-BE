package com.swu.atch.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

// 편집한 세부 작업. 사용자 입력 분을 보존하고 draftId로 개별 시간 예측 사용을 관리
@Schema(description = "편집한 세부 작업. 사용자 입력 분을 보존하고 draftId로 개별 시간 예측 사용을 관리")
public record DecomposedTaskInput(
        @NotNull @Schema(description = "AI 작업 초안 식별자. /api/v1/ai/drafts 또는 분해 결과에서 받은 UUID를 그대로 보냅니다. 사용자가 입력하거나 프론트가 임의로 생성하는 값이 아닙니다.", requiredMode = Schema.RequiredMode.REQUIRED) UUID draftId,
        @NotBlank @Size(max = 200) @Schema(example = "핵심 메시지 한 줄 정하기", requiredMode = Schema.RequiredMode.REQUIRED, description = "태스크명. 사용자가 입력하는 투두 이름(최대 200자).") String title,
        @NotNull @Positive @Schema(description = "사용자가 직접 입력한 예상 작업 소요시간(분). 0보다 큰 정수입니다. AI 제안시간으로 덮어쓰지 않습니다.", example = "10", requiredMode = Schema.RequiredMode.REQUIRED) Integer userEstimatedDurationMinutes
) {

}

