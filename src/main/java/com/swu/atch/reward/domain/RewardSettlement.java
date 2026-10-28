package com.swu.atch.reward.domain;

import com.swu.atch.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

// 사용자별 날짜 단위 보상 정산 완료 기록
@Getter
@Entity
@Table(
        name = "reward_settlements",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_reward_settlements_user_date",
                columnNames = {"user_id", "reward_date"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RewardSettlement {

    private static final BigDecimal HUNDRED =
            BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "reward_date", nullable = false, updatable = false)
    private LocalDate rewardDate;

    @Column(
            name = "assigned_todo_count",
            nullable = false,
            updatable = false
    )
    private Integer assignedTodoCount;

    @Column(
            name = "completed_todo_count",
            nullable = false,
            updatable = false
    )
    private Integer completedTodoCount;

    @Column(
            name = "completion_rate",
            nullable = false,
            precision = 5,
            scale = 2,
            updatable = false
    )
    private BigDecimal completionRate;

    @Column(name = "settled_at", nullable = false, updatable = false)
    private Instant settledAt;

    // 확정된 일일 Todo 수로 정산 결과 생성
    public static RewardSettlement settle(
            User user,
            LocalDate rewardDate,
            int assignedTodoCount,
            int completedTodoCount,
            Instant settledAt
    ) {
        Objects.requireNonNull(user, "사용자는 필수입니다.");
        Objects.requireNonNull(
                rewardDate, "정산 날짜는 필수입니다."
        );
        Objects.requireNonNull(
                settledAt, "정산 완료 시각은 필수입니다."
        );

        if (assignedTodoCount < 0
                || completedTodoCount < 0
                || completedTodoCount > assignedTodoCount) {
            throw new IllegalArgumentException(
                    "배정 및 완료 Todo 수가 올바르지 않습니다."
            );
        }

        BigDecimal rate = assignedTodoCount == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(completedTodoCount)
                .multiply(HUNDRED)
                .divide(
                        BigDecimal.valueOf(assignedTodoCount),
                        2,
                        RoundingMode.HALF_UP
                );

        RewardSettlement settlement = new RewardSettlement();

        settlement.user = user;
        settlement.rewardDate = rewardDate;
        settlement.assignedTodoCount = assignedTodoCount;
        settlement.completedTodoCount = completedTodoCount;
        settlement.completionRate = rate;
        settlement.settledAt = settledAt;

        return settlement;
    }
}
