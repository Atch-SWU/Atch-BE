package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

// 유사 작업 최대 3개. 이력 없음·색인 일부 준비·외부 기능 장애를 구분
@Schema(description = "유사 작업 최대 3개. 이력 없음·색인 일부 준비·외부 기능 장애를 구분")
public record SimilarTodoSearchResponse(
        @Schema(description = "유사 작업 검색 상태. AVAILABLE=검색 가능, PARTIAL=일부 기록만 준비됨, NO_HISTORY=유효 이력 없음, DISABLED=비활성, UNAVAILABLE=검색 이용 불가입니다.")
        VoyageStatus voyageStatus,
        @Schema(description = "조회한 항목 목록입니다.")
        List<SimilarTodoSummary> items,
        @Schema(description = "조회·계산에 사용한 기준 시각(UTC)입니다. 새로 조회해도 보존된 계산 결과는 원래 기준 시각을 유지할 수 있습니다.")
        Instant asOf
) {
    public enum VoyageStatus { AVAILABLE, PARTIAL, NO_HISTORY, DISABLED, UNAVAILABLE }
}

