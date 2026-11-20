package com.swu.atch.reward.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "카테고리 화면용 아이템 목록. 보유 여부와 관계없이 활성 아이템을 포함하고 아이템 ID 오름차순으로 반환합니다.")
public record CharacterItemCatalogPageResponse(
        @Schema(description = "현재 페이지의 아이템입니다. 비활성 아이템은 본인이 보유한 경우에만 포함합니다.")
        List<CharacterItemCatalogEntryResponse> items,
        @Schema(description = "현재 페이지 번호. 0부터 시작합니다.") int page,
        @Schema(description = "적용된 페이지당 항목 수입니다.") int size,
        @Schema(description = "노출 조건과 필터에 맞는 전체 아이템 수입니다.") long totalElements,
        @Schema(description = "전체 페이지 수입니다.") int totalPages,
        @Schema(description = "다음 페이지가 있는지 나타냅니다.") boolean hasNext,
        @Schema(description = "조회 기준 시각(UTC)입니다.") Instant asOf
) { }
