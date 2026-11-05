package com.swu.atch.reward.dto.response;

import com.swu.atch.reward.domain.ItemCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "카테고리별 장착 슬롯. 빈 슬롯은 item·equippedAt·updatedAt이 null입니다.")
public record EquippedCharacterSlotResponse(
        @Schema(description = "슬롯 카테고리. COLOR·BACKGROUND·EYES·ACCESSORY 중 하나입니다.") ItemCategory category,
        @Schema(description = "장착 아이템. 빈 슬롯이면 null이며, 기본 캐릭터 모습은 프론트에서 렌더링합니다.") CharacterItemResponse item,
        @Schema(description = "현재 아이템을 장착한 시각(UTC). 같은 아이템의 PUT 재전송으로 갱신하지 않습니다.") Instant equippedAt,
        @Schema(description = "슬롯 행의 마지막 갱신 시각(UTC). 빈 슬롯이면 null입니다.") Instant updatedAt
) {
}
