package com.swu.atch.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// 최종 저장은 예측 요청과 달리 예상시간이 없는 세부 작업도 받는다.
public record DecomposedTaskSaveInput(
        @NotNull @Schema(description = "분해 결과 또는 자식 초안 발급 응답의 UUID입니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID draftId,
        @NotBlank @Size(max = 200) @Schema(description = "최종 확정할 세부 작업명입니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Positive @Schema(description = "직접 입력한 예상 작업 소요시간(분, 선택). 미입력은 생략하거나 null로 보냅니다.", example = "10", nullable = true)
        Integer userEstimatedDurationMinutes
) {}
