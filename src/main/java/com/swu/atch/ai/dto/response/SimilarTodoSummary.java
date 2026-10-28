package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;


// 유사 완료 작업 요약. cosineDistance는 작을수록 유사하며 벡터는 공개하지 않음
@Schema(description = "유사 완료 작업 요약. cosineDistance는 작을수록 유사하며 벡터는 공개하지 않음")
public record SimilarTodoSummary(
        Long todoId, String title, int userEstimatedDurationMinutes,
        long actualDurationSeconds, double cosineDistance
) {

}

