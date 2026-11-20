package com.swu.atch.reward.admin;

import com.swu.atch.reward.dto.response.CharacterItemResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminCatalogItemResponse(
        @Schema(description = "운영진 등록 코드. 이전 방식으로 등록된 아이템은 null입니다.", nullable = true) String itemCode,
        CharacterItemResponse item
) { }
