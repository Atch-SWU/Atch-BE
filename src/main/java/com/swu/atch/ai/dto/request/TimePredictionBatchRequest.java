package com.swu.atch.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

// 세부 작업별 사용자 시간을 보정. 각 작업의 성공 사용 이력과 입력 일치를 따로 확인
@Schema(description = "세부 작업별 사용자 시간을 보정. 각 작업의 성공 사용 이력과 입력 일치를 따로 확인")
public record TimePredictionBatchRequest(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "분해한 원본 작업의 초안 UUID. 분해 응답에서 받은 값을 그대로 보냅니다. 저장된 부모 투두 ID가 아닙니다.") UUID decompositionDraftId,
        @NotBlank @Size(max = 200) @Schema(example = "기획안 초안 작성", requiredMode = Schema.RequiredMode.REQUIRED, description = "원본 태스크명(필수). 분해 전 원본 작업 이름을 보냅니다(최대 200자). 최종 저장에도 같은 값을 사용합니다.") String sourceTaskName,
        @Schema(description = "메모(선택). 작업에 참고할 내용을 입력합니다(최대 2000자).")
        @Size(max = 2000) String memo,
        @NotNull @Size(min = 1, max = 20) @Valid
        @Schema(description = "세부 작업 배열(1~20개). 각 작업의 서버 발급 draftId·편집한 태스크명·사용자 직접 입력시간을 담습니다. 순서는 화면에 표시할 순서입니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotNull DecomposedTaskInput> tasks
) {

}

