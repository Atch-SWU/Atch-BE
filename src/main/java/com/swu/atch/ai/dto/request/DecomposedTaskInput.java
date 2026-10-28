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
        @NotNull @Schema(description = "서버가 발급한 해당 세부 작업의 초안 ID. 화면에서도 안정적인 항목 식별자로 사용", requiredMode = Schema.RequiredMode.REQUIRED) UUID draftId,
        @NotBlank @Size(max = 200) @Schema(example = "핵심 메시지 한 줄 정하기", requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @NotNull @Positive @Schema(description = "사용자가 직접 입력한 분. AI 제안값으로 덮어쓰지 않음", example = "10", requiredMode = Schema.RequiredMode.REQUIRED) Integer userEstimatedDurationMinutes
) {

}

