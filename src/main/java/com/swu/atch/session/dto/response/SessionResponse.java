package com.swu.atch.session.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.session.domain.SessionEndReason;
import java.time.Instant;

// 집중 구간. 열린 구간의 종료 시각·저장 초·종료 사유는 모두 null
@Schema(description = "집중 구간. 열린 구간의 종료 시각·저장 초·종료 사유는 모두 null")
public record SessionResponse(
        @Schema(description = "작업 세션 ID. 타이머 시작·일시정지·재개·전환의 기준 세션을 지정할 때 사용합니다.")
        Long id,
        @Schema(description = "서버가 발급한 투두 ID. 투두 생성·조회 응답에서 받은 값입니다.")
        Long todoId,
        @Schema(description = "작업 세션 시작 시각(UTC)입니다.")
        Instant startedAt,
        @Schema(description = "작업 세션 종료 시각(UTC). 실행 중인 세션은 null입니다.")
        Instant endedAt,
        @Schema(description = "이 작업 세션에 저장된 실제 집중시간(초). 실행 중이면 null입니다.")
        Integer durationSeconds,
        @Schema(description = "세션 종료 이유. PAUSED=일시정지, COMPLETED=작업 완료, SWITCHED=다른 작업으로 전환입니다. 실행 중이면 null입니다.")
        SessionEndReason endReason
) {

}

