package com.swu.atch.reward.dto.request;

import com.swu.atch.reward.domain.ItemCategory;
import com.swu.atch.reward.domain.ItemGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// 잠금 표시용 카탈로그의 필터. 보유 여부는 인증된 사용자를 기준으로 계산한다.
@Schema(description = "활성 아이템 전체와 본인이 보유한 비활성 아이템의 조회 조건")
public record CharacterItemCatalogQuery(
        @Schema(description = "카테고리 필터(선택). COLOR=색상, BACKGROUND=배경, EYES=눈, ACCESSORY=액세서리입니다. 생략하면 전체 카테고리입니다.")
        ItemCategory category,
        @Schema(description = "보상 등급 필터(선택). B·A·S 중 하나이며 생략하면 전체 등급입니다.")
        ItemGrade grade,
        @Min(0) @Schema(description = "페이지 번호. 0부터 시작하며 생략하면 0입니다.") Integer page,
        @Min(1) @Max(100) @Schema(description = "페이지당 항목 수. 기본 20, 허용 범위 1~100입니다.") Integer size
) {
    public CharacterItemCatalogQuery {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
