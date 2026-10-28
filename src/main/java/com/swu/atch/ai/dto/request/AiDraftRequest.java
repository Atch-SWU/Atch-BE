package com.swu.atch.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

// AI 기능을 사용할 작업 초안 식별자 발급. 빈 객체는 신규 작업, todoId는 기존 본인 Todo, parentDraftId는 수동 추가 항목
@Schema(description = "AI 기능을 사용할 작업 초안 식별자 발급. 빈 객체는 신규 작업, todoId는 기존 본인 Todo, parentDraftId는 수동 추가 항목")
public record AiDraftRequest(
        @Positive @Schema(description = "기존 투두의 AI 초안을 받을 때만 보내는 본인 투두 ID. 신규 작업은 생략합니다. parentDraftId와 동시에 입력할 수 없습니다.", example = "1") Long todoId,
        @Schema(description = "수동 추가 세부 작업이 속할 원본 초안의 UUID. 분해 응답의 decompositionDraftId를 보냅니다. todoId와 동시에 입력할 수 없습니다.") java.util.UUID parentDraftId
) {

}

