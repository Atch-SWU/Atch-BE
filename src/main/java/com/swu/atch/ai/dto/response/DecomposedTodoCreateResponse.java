package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.todo.domain.SelectedEstimateType;
import com.swu.atch.todo.dto.response.TodoResponse;
import java.util.List;
import java.util.UUID;

// 일괄 생성 결과. 사용자 시간과 AI 시간을 각각 보존하며 실제 ID를 초안 ID에 연결
@Schema(description = "일괄 생성 결과. 사용자 시간과 AI 시간을 각각 보존하며 실제 ID를 초안 ID에 연결")
public record DecomposedTodoCreateResponse(
        UUID creationRequestId, List<CreatedTodo> items, long totalUserEstimatedDurationMinutes,
        Long totalAiEstimatedDurationMinutes, SelectedEstimateType selectedEstimateType, boolean replayed
) {
    public record CreatedTodo(UUID draftId, TodoResponse todo) { }
}

