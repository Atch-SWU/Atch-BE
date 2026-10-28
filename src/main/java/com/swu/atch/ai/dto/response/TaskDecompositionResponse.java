package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 이름·순서만 있는 분해 초안. 시간은 사용자 입력 전 빈 상태이며 아직 Todo가 생성되지 않음
@Schema(description = "이름·순서만 있는 분해 초안. 시간은 사용자 입력 전 빈 상태이며 아직 Todo가 생성되지 않음")
public record TaskDecompositionResponse(
        @Schema(description = "분해 원본의 초안 UUID. 묶음 예측·최종 저장 요청에 그대로 사용합니다.")
        UUID decompositionDraftId,
        @Schema(description = "AI 세부 분량 나누기 전 원본 태스크명. 일반 투두는 null일 수 있습니다.")
        String sourceTaskName,
        @Schema(description = "AI가 제안한 세부 작업 목록. 사용자는 태스크명·순서를 편집하고 항목을 추가·삭제할 수 있습니다.")
        List<Step> steps,
        @Schema(description = "이 작업의 AI 기능 사용 상태·재시도 가능 여부입니다.")
        AiUsageResponse usage,
        @Schema(description = "새로운 AI 호출 없이 보존 결과를 반환했는지 여부입니다.")
        boolean cached,
        @Schema(description = "이 응답에 포함된 임시 초안·제안 또는 대기 요청의 만료 시각(UTC)입니다.")
        Instant expiresAt
) {
    public record Step(
            @Schema(description = "이 작업에 연결된 서버 발급 AI 초안 UUID. 후속 예측·저장 요청에서 같은 값을 사용합니다.")
            UUID draftId,
            @Schema(description = "AI가 제안한 세부 작업의 순서입니다. 응답 배열의 순서로 표시할 수 있습니다.")
            int position,
            @Schema(description = "태스크명(투두 이름)입니다.")
            String title) { }
}

