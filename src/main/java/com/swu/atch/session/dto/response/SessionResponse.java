package com.swu.atch.session.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.session.domain.SessionEndReason;
import java.time.Instant;

// 집중 구간. 열린 구간의 종료 시각·저장 초·종료 사유는 모두 null
@Schema(description = "집중 구간. 열린 구간의 종료 시각·저장 초·종료 사유는 모두 null")
public record SessionResponse(
        Long id, Long todoId, Instant startedAt, Instant endedAt,
        Integer durationSeconds, SessionEndReason endReason
) {

}

