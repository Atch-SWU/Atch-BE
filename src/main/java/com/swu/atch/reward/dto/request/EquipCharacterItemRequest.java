package com.swu.atch.reward.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "보유 아이템을 지정한 슬롯에 장착하는 요청")
public record EquipCharacterItemRequest(
        @NotNull @Positive
        @Schema(description = "보유 목록 응답의 item.id를 복사합니다. 보유 관계 ID가 아닌 캐릭터 아이템 ID입니다. 사용자가 직접 입력하는 값이 아닙니다.",
                example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        Long characterItemId
) {
}
