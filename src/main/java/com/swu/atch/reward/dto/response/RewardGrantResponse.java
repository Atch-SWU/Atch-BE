package com.swu.atch.reward.dto.response;

import com.swu.atch.reward.domain.ItemGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "실제로 지급된 아이템 한 개. 확인 팝업의 중복 표시 방지는 클라이언트가 관리합니다.")
public record RewardGrantResponse(
        @Schema(description = "지급 이력 ID. 클라이언트는 로그인 사용자와 이 ID를 함께 사용해 팝업 표시 여부를 기억합니다.") Long grantId,
        @Schema(description = "지급 등급. item.grade와 반드시 같습니다.") ItemGrade rewardGrade,
        @Schema(description = "실제 지급된 아이템의 현재 카탈로그 정보입니다.") CharacterItemResponse item,
        @Schema(description = "실제 지급 시각(UTC). rewardDate의 자정과 같지 않을 수 있습니다.") Instant grantedAt
) {
}
