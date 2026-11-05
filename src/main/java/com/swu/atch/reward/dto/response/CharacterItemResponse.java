package com.swu.atch.reward.dto.response;

import com.swu.atch.reward.domain.ItemCategory;
import com.swu.atch.reward.domain.ItemGrade;
import io.swagger.v3.oas.annotations.media.Schema;

// 이름·자산 키·active는 조회 시점 카탈로그 값이며, 과거 모습의 스냅샷이 아니다.
@Schema(description = "캐릭터 아이템의 현재 카탈로그 정보")
public record CharacterItemResponse(
        @Schema(description = "캐릭터 아이템 ID. 장착 요청의 characterItemId에 사용합니다.") Long id,
        @Schema(description = "아이템 이름입니다.") String name,
        @Schema(description = "아이템 등급. B·A·S 중 하나이며 등록 이후 변경하지 않습니다.") ItemGrade grade,
        @Schema(description = "장착 카테고리. COLOR=색상, BACKGROUND=배경, EYES=눈, ACCESSORY=액세서리입니다.") ItemCategory category,
        @Schema(description = "프론트 렌더링용 이미지 자산 경로 또는 키입니다. 반드시 완성된 HTTP URL인 것은 아닙니다.") String assetKey,
        @Schema(description = "현재 신규 지급 가능한지 나타냅니다. false인 보유 아이템도 조회·장착할 수 있습니다.") boolean active
) {
}
