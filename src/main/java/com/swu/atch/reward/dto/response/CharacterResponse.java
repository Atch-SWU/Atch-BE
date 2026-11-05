package com.swu.atch.reward.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "본인의 현재 캐릭터 장착 상태와 활성 아이템 수집 현황")
public record CharacterResponse(
        @Schema(description = "COLOR·BACKGROUND·EYES·ACCESSORY 순서의 4개 슬롯입니다. 미장착 슬롯도 포함합니다.") List<EquippedCharacterSlotResponse> slots,
        @Schema(description = "조회 시점 활성 카탈로그 기준 전체 수집 여부입니다.") CharacterCollectionResponse collection,
        @Schema(description = "조회 기준 시각(UTC)입니다.") Instant asOf
) {
}
