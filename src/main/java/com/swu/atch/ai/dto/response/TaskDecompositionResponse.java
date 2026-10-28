package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 이름·순서만 있는 분해 초안. 시간은 사용자 입력 전 빈 상태이며 아직 Todo가 생성되지 않음
@Schema(description = "이름·순서만 있는 분해 초안. 시간은 사용자 입력 전 빈 상태이며 아직 Todo가 생성되지 않음")
public record TaskDecompositionResponse(
        UUID decompositionDraftId, String sourceTaskName, List<Step> steps,
        AiUsageResponse usage, boolean cached, Instant expiresAt
) {
    public record Step(UUID draftId, int position, String title) { }
}

