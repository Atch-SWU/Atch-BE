package com.swu.atch.session.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.todo.dto.response.TodoResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 완료된 Todo의 누적 집중 기록과 다음 작업 후보. 기존 완료 API 응답은 유지
@Schema(description = "완료된 Todo의 누적 집중 기록과 다음 작업 후보. 기존 완료 API 응답은 유지")
public record TodoCompletionSummaryResponse(
        TodoResponse todo, long pauseCount, long resumeCount, long switchCount,
        long deadlineOverrunSeconds, long estimateOverrunSeconds, List<NextTodo> nextTodos
) {
    public record NextTodo(Long id, String title, LocalDate currentScheduleDate,
                           Instant dueAt, int estimatedDurationMinutes) { }
}

