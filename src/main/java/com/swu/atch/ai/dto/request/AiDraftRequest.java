package com.swu.atch.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

// AI 기능을 사용할 작업 초안 식별자 발급. 빈 객체는 신규 작업, todoId는 기존 본인 Todo
@Schema(description = "AI 기능을 사용할 작업 초안 식별자 발급. 빈 객체는 신규 작업, todoId는 기존 본인 Todo")
public record AiDraftRequest(
        @Positive @Schema(description = "기존 Todo의 AI 사용 이력을 이어받을 때만 지정. 임의 새 draft로 기존 제한을 초기화하지 않음", example = "1") Long todoId
) {

}

