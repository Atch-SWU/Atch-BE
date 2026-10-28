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
        @NotNull @Schema(description = "서버 발급 draftId. 편집·거절·제안 토큰 만료로 성공 사용 이력이 초기화되지 않음", requiredMode = Schema.RequiredMode.REQUIRED) UUID draftId,
        @NotBlank @Size(max = 200) @Schema(example = "기획안 초안 작성", requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Size(max = 200) String sourceTaskName,
        @Size(max = 2000) String memo,
        @NotNull @Positive @Schema(example = "30", requiredMode = Schema.RequiredMode.REQUIRED) Integer userEstimatedDurationMinutes
) {

}

