package com.swu.atch.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.todo.domain.TodoStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 일별 배정 이력. 과거 제목·예상시간은 Snapshot에 없으므로 당시 값으로 제공하지 않음
@Schema(description = "일별 배정 이력. 과거 제목·예상시간은 Snapshot에 없으므로 당시 값으로 제공하지 않음")
public record DailyTodoHistoryResponse(
        @Schema(description = "조회 대상 날짜(서울 기준)입니다.")
        LocalDate date,
        @Schema(description = "일별 기록 상태. LIVE=오늘 실시간, FINALIZED=과거 확정, PENDING_FINALIZATION=확정 대기, UNAVAILABLE=자료 없음입니다.")
        DailyReportResponse.State state,
        @Schema(description = "조회한 항목 목록입니다.")
        List<SnapshotItem> items,
        @Schema(description = "조회·계산에 사용한 기준 시각(UTC)입니다. 새로 조회해도 보존된 계산 결과는 원래 기준 시각을 유지할 수 있습니다.")
        Instant asOf
) {
    public record SnapshotItem(
            @Schema(description = "서버가 발급한 투두 ID. 투두 생성·조회 응답에서 받은 값입니다.")
            Long todoId,
            @Schema(description = "조회 시점의 제목. 과거 제목이 아님") String currentTitle,
            @Schema(description = "해당 날짜 마감 당시 확정한 투두 진행 상태입니다.")
            TodoStatus statusAtDayEnd,
            @Schema(description = "해당 날짜 확정 당시의 최초 예정 시작 일시(UTC)입니다.")
            Instant startAtSnapshot,
            @Schema(description = "해당 날짜 확정 당시의 최초 마감 일시(UTC)입니다.")
            Instant dueAtSnapshot,
            @Schema(description = "해당 날짜 확정 당시의 완료 시각(UTC). 미완료이면 null입니다.")
            Instant completedAtSnapshot,
            @Schema(description = "해당 날짜로 들어온 이월 작업이었는지 여부입니다.")
            boolean wasRolledOver,
            @Schema(description = "해당 날짜 확정 당시 마감 초과 상태였는지 여부입니다.")
            boolean wasOverdue,
            @Schema(description = "날짜별 기록을 확정한 시각(UTC)입니다.")
            Instant snapshotAt) { }
}

