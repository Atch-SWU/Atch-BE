package com.swu.atch.reward.dto.response;

import com.swu.atch.reward.domain.ItemGrade;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "서울 기준 오늘의 투두 완료율과 다음 아이템 보상 목표")
public record RewardProgressResponse(
        @Schema(description = "집계한 서울 날짜입니다.") LocalDate date,
        @Schema(description = "집계 기준 시각(UTC)입니다.") Instant asOf,
        @Schema(description = "오늘 배정된 투두 수입니다.") int assignedTodoCount,
        @Schema(description = "오늘 배정된 투두 중 완료된 수입니다.") int completedTodoCount,
        @Schema(description = "현재 완료율(0~100%)입니다. 소수 둘째 자리까지 버림하며 배정이 없으면 0입니다.") BigDecimal completionPercent,
        @Schema(description = "활성 미보유 아이템이 있는 다음 목표입니다. 오늘 배정 또는 남은 목표가 없으면 null입니다.", nullable = true)
        NextReward nextReward
) {
    public record NextReward(
            @Schema(description = "가장 가까운 보상 목표 등급입니다.", example = "B") ItemGrade grade,
            @Schema(description = "이 등급의 완료율 기준(%)입니다.", example = "50") int targetCompletionPercent,
            @Schema(description = "현재 완료율에서 목표까지의 차이(%p)입니다. 소수 둘째 자리까지 올림합니다.", example = "2")
            BigDecimal remainingPercentagePoints,
            @Schema(description = "현재 배정에서 목표에 도달하려면 더 완료해야 하는 최소 투두 수입니다.", example = "1")
            int remainingTodoCount
    ) { }
}
