package com.swu.atch.reward.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "보유 아이템 목록. 획득 시각 내림차순, 같은 시각이면 보유 관계 ID 내림차순입니다.")
public record OwnedCharacterItemPageResponse(
        @Schema(description = "현재 페이지의 보유 아이템입니다. 비활성화된 보유 아이템도 포함합니다.") List<OwnedCharacterItemResponse> items,
        @Schema(description = "현재 페이지 번호. 0부터 시작합니다.") int page,
        @Schema(description = "적용된 페이지당 항목 수입니다.") int size,
        @Schema(description = "필터에 맞는 전체 보유 아이템 수입니다.") long totalElements,
        @Schema(description = "전체 페이지 수입니다.") int totalPages,
        @Schema(description = "다음 페이지가 있는지 나타냅니다.") boolean hasNext,
        @Schema(description = "조회 기준 시각(UTC)입니다.") Instant asOf
) {
}
