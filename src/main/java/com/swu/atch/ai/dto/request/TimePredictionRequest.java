package com.swu.atch.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

// 사용자 입력시간을 통계·맥락으로 보정. 동일 작업은 성공한 시간 예측 1회만 허용
@Schema(description = "사용자 입력시간을 통계·맥락으로 보정. 동일 작업은 성공한 시간 예측 1회만 허용")
public record TimePredictionRequest(
        @NotNull @Schema(description = "AI 작업 초안 식별자. /api/v1/ai/drafts 또는 분해 결과에서 받은 UUID를 그대로 보냅니다. 사용자가 입력하거나 프론트가 임의로 생성하는 값이 아닙니다.", requiredMode = Schema.RequiredMode.REQUIRED) UUID draftId,
        @NotBlank @Size(max = 200) @Schema(example = "기획안 초안 작성", requiredMode = Schema.RequiredMode.REQUIRED, description = "태스크명. 사용자가 입력하는 투두 이름(최대 200자).") String title,
        @Schema(description = "원본 태스크명(선택). AI 세부 분량 나누기로 만든 작업이라면 분해 전 원본 이름을 전달합니다(최대 200자).")
        @Size(max = 200) String sourceTaskName,
        @Schema(description = "메모(선택). 작업에 참고할 내용을 입력합니다(최대 2000자).")
        @Size(max = 2000) String memo,
        @NotNull @Positive @Schema(example = "30", requiredMode = Schema.RequiredMode.REQUIRED, description = "사용자가 직접 입력한 예상 작업 소요시간(분). 0보다 큰 정수입니다. AI 제안시간으로 덮어쓰지 않습니다.") Integer userEstimatedDurationMinutes
) {

}

