package com.swu.atch.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

// 본인 유효 완료 작업의 유사 검색. 벡터·모델·다른 사용자 ID를 입력하지 않음
@Schema(description = "본인 유효 완료 작업의 유사 검색. 벡터·모델·다른 사용자 ID를 입력하지 않음")
public record SimilarTodoSearchRequest(
        @NotBlank @Size(max = 200) @Schema(example = "기획안 초안 작성", requiredMode = Schema.RequiredMode.REQUIRED, description = "태스크명. 사용자가 입력하는 투두 이름(최대 200자).") String title,
        @Size(max = 200) @Schema(description = "원본 태스크명(선택). AI 세부 분량 나누기로 만든 작업이라면 분해 전 원본 이름을 전달합니다(최대 200자).") String sourceTaskName,
        @Schema(description = "메모(선택). 작업에 참고할 내용을 입력합니다(최대 2000자).")
        @Size(max = 2000) String memo,
        @Positive @Schema(description = "검색 결과에서 제외할 본인 투두 ID(선택). 기존 투두의 유사 작업을 찾을 때 자기 자신을 제외하는 데 사용합니다.") Long excludeTodoId
) {

}

