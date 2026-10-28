package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;


// 유사 완료 작업 요약. cosineDistance는 작을수록 유사하며 벡터는 공개하지 않음
@Schema(description = "유사 완료 작업 요약. cosineDistance는 작을수록 유사하며 벡터는 공개하지 않음")
public record SimilarTodoSummary(
        @Schema(description = "서버가 발급한 투두 ID. 투두 생성·조회 응답에서 받은 값입니다.")
        Long todoId,
        @Schema(description = "태스크명(투두 이름)입니다.")
        String title,
        @Schema(description = "사용자가 직접 입력한 예상 작업 소요시간(분). AI 제안 수락 여부와 별도로 보존한 원본 값입니다.")
        int userEstimatedDurationMinutes,
        @Schema(description = "종료된 작업 세션의 실제 집중시간 합계(초). 실행 중인 구간의 경과시간은 포함하지 않습니다.")
        long actualDurationSeconds,
        @Schema(description = "작업 간 유사도 거리. 작을수록 유사하며 확률이나 일치율이 아닙니다.")
        double cosineDistance
) {

}

