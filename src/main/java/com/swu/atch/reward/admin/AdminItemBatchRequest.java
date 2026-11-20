package com.swu.atch.reward.admin;

import com.swu.atch.reward.domain.ItemCategory;
import com.swu.atch.reward.domain.ItemGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

@Schema(description = "업로드 파일별 아이템 정보. files와 같은 순서로 fileIndex를 지정합니다.")
public record AdminItemBatchRequest(
        @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid Item> items
) {
    public record Item(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,79}")
            @Schema(description = "등록 요청을 식별할 고유 코드입니다. 같은 항목을 재전송할 때 같은 코드를 사용합니다.", example = "chef-hat")
            String itemCode,
            @NotBlank @Size(max = 100) @Schema(description = "앱에 표시할 아이템 이름입니다.", example = "요리사 모자") String name,
            @NotNull @Schema(description = "지급 후보에 적용할 보상 등급. B·A·S 중 하나입니다.") ItemGrade grade,
            @NotNull @Schema(description = "COLOR=색상, BACKGROUND=배경, EYES=눈, ACCESSORY=액세서리입니다.") ItemCategory category,
            @NotNull @Min(0) @Max(19) @Schema(description = "files 배열의 파일 위치. 첫 번째 파일은 0입니다.", example = "0") Integer fileIndex
    ) { }
}
