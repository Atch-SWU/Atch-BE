package com.swu.atch.reward.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "잠금 표시용 아이템과 현재 로그인 사용자의 보유·장착 상태")
public record CharacterItemCatalogEntryResponse(
        @Schema(description = "공통 아이템 정보. item.id로 상세를 조회하고, 보유한 경우 장착 요청의 characterItemId로 사용합니다.")
        CharacterItemResponse item,
        @Schema(description = "본인의 보유 여부입니다. false이면 잠금 상태로 표시하고 장착 버튼을 비활성화합니다.") boolean owned,
        @Schema(description = "본인이 현재 장착한 아이템인지 나타냅니다. 미획득 아이템은 false입니다.") boolean equipped,
        @Schema(description = "본인이 획득한 시각(UTC)입니다. 미획득 아이템은 null입니다.", nullable = true) Instant acquiredAt
) { }
