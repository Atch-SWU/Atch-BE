package com.swu.atch.reward.dto.response;

import com.swu.atch.reward.domain.ItemGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Snapshot 확정 후 한 번 저장한 일일 보상 결과. 이후 Todo 편집·완료 취소로 다시 계산하지 않습니다.")
public record RewardSettlementResponse(
        @Schema(description = "일일 보상 정산 기록 ID입니다.") Long settlementId,
        @Schema(description = "보상 대상 서울 날짜(YYYY-MM-DD)입니다. 실제 지급 날짜와 다를 수 있습니다.") LocalDate rewardDate,
        @Schema(description = "확정 Snapshot 기준 해당 날짜에 배정된 Todo 수입니다.") int assignedTodoCount,
        @Schema(description = "배정된 Todo 중 일 종료 시 완료된 Todo 수입니다.") int completedTodoCount,
        @Schema(description = "확정 완료율(%). 0~100, 소수 둘째 자리 HALF_UP이며 배정 0개는 0.00입니다. 등급 판정에는 반올림 전 원본 수를 사용합니다.", example = "75.00") BigDecimal completionRate,
        @Schema(description = "원본 수로 판정한 지급 대상 등급(B·A·S 순서)입니다. 50% 이상 B, 75% 이상 B/A, 100% B/A/S입니다. 지급 후보 유무와는 별개입니다.") List<ItemGrade> eligibleGrades,
        @Schema(description = "실제 지급 결과 0~3개(B·A·S 순서)입니다. 대상 등급이어도 활성 미보유 후보가 없으면 지급하지 않습니다. 빈 목록도 정산 완료 결과입니다.") List<RewardGrantResponse> grants,
        @Schema(description = "정산 완료 시각(UTC)입니다. 조회해도 바뀌지 않습니다.") Instant settledAt
) {
}
