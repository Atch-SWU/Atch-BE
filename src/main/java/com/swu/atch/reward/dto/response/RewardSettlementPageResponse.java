package com.swu.atch.reward.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "확정된 일별 보상 정산 목록. 보상 날짜 내림차순이며 지급이 없는 정산도 포함합니다.")
public record RewardSettlementPageResponse(
        @Schema(description = "적용한 조회 시작 날짜(포함)입니다.") LocalDate from,
        @Schema(description = "적용한 조회 종료 날짜(포함)입니다.") LocalDate to,
        @Schema(description = "현재 페이지의 일별 정산 결과입니다. 추적 시작 전처럼 근거 없는 날짜는 만들지 않습니다.") List<RewardSettlementResponse> items,
        @Schema(description = "현재 페이지 번호. 0부터 시작합니다.") int page,
        @Schema(description = "적용된 페이지당 정산 날짜 수입니다.") int size,
        @Schema(description = "조회 기간의 전체 정산 기록 수입니다.") long totalElements,
        @Schema(description = "전체 페이지 수입니다.") int totalPages,
        @Schema(description = "다음 페이지가 있는지 나타냅니다. 여러 페이지의 새 지급 결과를 확인할 때 사용합니다.") boolean hasNext,
        @Schema(description = "조회 기준 시각(UTC)입니다.") Instant asOf
) {
}
