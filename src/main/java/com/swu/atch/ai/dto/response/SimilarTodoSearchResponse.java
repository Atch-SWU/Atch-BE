package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

// 유사 작업 최대 3개. 이력 없음·색인 일부 준비·외부 기능 장애를 구분
@Schema(description = "유사 작업 최대 3개. 이력 없음·색인 일부 준비·외부 기능 장애를 구분")
public record SimilarTodoSearchResponse(
        VoyageStatus voyageStatus, List<SimilarTodoSummary> items, Instant asOf
) {
    public enum VoyageStatus { AVAILABLE, PARTIAL, NO_HISTORY, DISABLED, UNAVAILABLE }
}

