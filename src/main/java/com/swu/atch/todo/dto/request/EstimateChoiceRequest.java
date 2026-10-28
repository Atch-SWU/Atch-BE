package com.swu.atch.todo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.todo.domain.SelectedEstimateType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

// 사용자 시간과 서버 AI 제안의 선택. 단건 Todo 연결은 후속 AI Service 단계
@Schema(description = "사용자 시간과 서버 AI 제안의 선택. 단건 Todo 연결은 후속 AI Service 단계")
public record EstimateChoiceRequest(
        @NotNull @Schema(description = "단건은 해당 초안, 묶음은 원본 초안 ID. USER 선택에도 사용 이력을 생성 Todo와 연결", requiredMode = Schema.RequiredMode.REQUIRED) UUID draftId,
        @NotNull @Schema(description = "분해 확정 시 모든 세부 작업에 동일하게 적용", example = "AI", requiredMode = Schema.RequiredMode.REQUIRED) SelectedEstimateType selectedEstimateType,
        @Size(max = 8192) @Schema(description = "AI 선택 시 필수. USER 선택에도 유효한 제안 토큰을 전달하면 거절한 AI 제안값을 함께 보존") String predictionToken
) {

}

