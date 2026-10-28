package com.swu.atch.todo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.todo.domain.SelectedEstimateType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

// 사용자 직접 입력시간과 서버 AI 제안 중 최종 저장에 사용할 값을 선택한다.
@Schema(description = "투두 저장 시 사용할 예상시간 선택 정보. 단건은 해당 작업 draftId, 세부 작업 묶음은 원본 decompositionDraftId를 사용합니다.")
public record EstimateChoiceRequest(
        @NotNull @Schema(description = "AI 작업 초안 식별자. /api/v1/ai/drafts 또는 분해 결과에서 받은 UUID를 그대로 보냅니다. 사용자가 입력하거나 프론트가 임의로 생성하는 값이 아닙니다.", requiredMode = Schema.RequiredMode.REQUIRED) UUID draftId,
        @NotNull @Schema(description = "최종 사용할 예상시간. USER=직접 입력시간, AI=AI 제안시간입니다. AI 선택 시 predictionToken이 필요합니다.", example = "AI", requiredMode = Schema.RequiredMode.REQUIRED) SelectedEstimateType selectedEstimateType,
        @Size(max = 8192) @Schema(description = "AI 제안 확인용 토큰. 시간 예측 응답 값을 그대로 보냅니다(10분 유효). 로그인 토큰이 아닙니다. AI 수락 시 필수이며 USER 선택 시에도 전달하면 거절한 제안을 보존할 수 있습니다.") String predictionToken
) {

}

