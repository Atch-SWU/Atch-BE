package com.swu.atch.reward.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "본인이 획득한 캐릭터 아이템")
public record OwnedCharacterItemResponse(
        @Schema(description = "아이템 정보. item.id를 장착 요청에 복사합니다.") CharacterItemResponse item,
        @Schema(description = "해당 아이템을 획득한 시각(UTC)입니다.") Instant acquiredAt,
        @Schema(description = "현재 해당 카테고리 슬롯에 이 아이템을 장착했는지 나타냅니다.") boolean equipped
) {
}
