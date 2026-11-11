package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.todo.domain.SelectedEstimateType;
import com.swu.atch.todo.dto.response.TodoResponse;
import java.util.List;
import java.util.UUID;

// 일괄 생성 결과. 사용자 시간과 AI 시간을 각각 보존하며 실제 ID를 초안 ID에 연결
@Schema(description = "일괄 생성 결과. 사용자 시간과 AI 시간을 각각 보존하며 실제 ID를 초안 ID에 연결")
public record DecomposedTodoCreateResponse(
        @Schema(description = "저장 요청에서 사용한 중복 방지 UUID입니다.")
        UUID creationRequestId,
        @Schema(description = "조회한 항목 목록입니다.")
        List<CreatedTodo> items,
        @Schema(description = "직접 입력한 항목의 예상시간 합계(분). 입력 항목이 없으면 null입니다.", nullable = true)
        Long totalUserEstimatedDurationMinutes,
        @Schema(description = "입력 항목 전체에 대한 검증된 AI 제안시간 합계(분). 제안이 없으면 null입니다.", nullable = true)
        Long totalAiEstimatedDurationMinutes,
        @Schema(description = "예상시간을 입력한 항목에 적용한 선택. 입력 항목이 없으면 null입니다.", nullable = true)
        SelectedEstimateType selectedEstimateType,
        @Schema(description = "동일한 저장 요청을 다시 받아 기존 저장 결과를 반환했는지 여부입니다.")
        boolean replayed
) {
    public record CreatedTodo(
            @Schema(description = "이 작업에 연결된 서버 발급 AI 초안 UUID. 후속 예측·저장 요청에서 같은 값을 사용합니다.")
            UUID draftId,
            @Schema(description = "해당 투두의 상세 정보입니다.")
            TodoResponse todo) { }
}

