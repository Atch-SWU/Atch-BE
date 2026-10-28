package com.swu.atch.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.todo.domain.TodoStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 일별 배정 이력. 과거 제목·예상시간은 Snapshot에 없으므로 당시 값으로 제공하지 않음
@Schema(description = "일별 배정 이력. 과거 제목·예상시간은 Snapshot에 없으므로 당시 값으로 제공하지 않음")
public record DailyTodoHistoryResponse(
        LocalDate date, DailyReportResponse.State state, List<SnapshotItem> items, Instant asOf
) {
    public record SnapshotItem(Long todoId,
                       @Schema(description = "조회 시점의 제목. 과거 제목이 아님") String currentTitle,
                       TodoStatus statusAtDayEnd, Instant startAtSnapshot, Instant dueAtSnapshot,
                       Instant completedAtSnapshot, boolean wasRolledOver, boolean wasOverdue,
                       Instant snapshotAt) { }
}

