package com.swu.atch.reward.dto.request;

import com.swu.atch.reward.domain.ItemCategory;
import com.swu.atch.reward.domain.ItemGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// 보유 목록의 필터만 받는다. 사용자 ID와 신규 지급 가능 여부는 입력받지 않는다.
@Schema(description = "본인이 보유한 캐릭터 아이템 조회 조건")
public record CharacterItemListQuery(
        @Schema(description = "등급 필터(선택). B·A·S 중 하나이며 생략하면 전체 등급입니다.")
        ItemGrade grade,
        @Schema(description = "카테고리 필터(선택). COLOR=색상, BACKGROUND=배경, EYES=눈, ACCESSORY=액세서리입니다.")
        ItemCategory category,
        @Min(0) @Schema(description = "페이지 번호. 0부터 시작하며 생략하면 0입니다.")
        Integer page,
        @Min(1) @Max(100) @Schema(description = "페이지당 항목 수. 기본 20, 허용 범위 1~100입니다.")
        Integer size
) {
    public CharacterItemListQuery {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
