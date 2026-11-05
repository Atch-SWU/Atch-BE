package com.swu.atch.reward.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 설계 제안: 활성 카탈로그가 비어 있으면 전체 수집으로 처리하지 않는다.
@Schema(description = "현재 활성 카탈로그 기준 수집 상태. 과거 정산 날짜의 수집 상태가 아닙니다.")
public record CharacterCollectionResponse(
        @Schema(description = "현재 신규 지급 가능한 활성 아이템 전체 수입니다. 비활성 아이템은 제외합니다.") long activeItemCount,
        @Schema(description = "본인이 보유한 활성 아이템 수입니다. 전체 보유 수와 다를 수 있습니다.") long ownedActiveItemCount,
        @Schema(description = "활성 아이템 수가 1개 이상이고 그 전부를 보유하면 true입니다. 활성 아이템이 0개면 false입니다. 팝업 확인 상태는 클라이언트가 사용자별로 관리합니다.") boolean allActiveItemsCollected
) {
}
