package com.swu.atch.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

// 본인 유효 완료 작업의 유사 검색. 벡터·모델·다른 사용자 ID를 입력하지 않음
@Schema(description = "본인 유효 완료 작업의 유사 검색. 벡터·모델·다른 사용자 ID를 입력하지 않음")
public record SimilarTodoSearchRequest(
        @NotBlank @Size(max = 200) @Schema(example = "기획안 초안 작성", requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Size(max = 200) @Schema(description = "선택적인 원본 작업명") String sourceTaskName,
        @Size(max = 2000) String memo,
        @Positive @Schema(description = "검색에서 제외할 본인 Todo. 타인 소유·없는 ID는 동일한 404") Long excludeTodoId
) {

}

