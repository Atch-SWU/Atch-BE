package com.swu.atch.reward.domain;

import com.swu.atch.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

// 일일 완료율에 따른 실제 아이템 지급 이력
@Getter
@Entity
@Table(
        name = "reward_grants",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_reward_grants_user_date_grade",
                        columnNames = {
                                "user_id", "reward_date", "reward_grade"
                        }
                ),
                @UniqueConstraint(
                        name = "uq_reward_grants_user_item",
                        columnNames = {
                                "user_id", "character_item_id"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RewardGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "character_item_id",
            nullable = false,
            updatable = false
    )
    private CharacterItem characterItem;

    @Column(name = "reward_date", nullable = false, updatable = false)
    private LocalDate rewardDate;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "reward_grade",
            nullable = false,
            length = 10,
            updatable = false
    )
    private ItemGrade rewardGrade;

    @Column(
            name = "completion_rate",
            nullable = false,
            precision = 5,
            scale = 2,
            updatable = false
    )
    private BigDecimal completionRate;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    // 확정된 일일 정산 결과를 기준으로 지급 이력 생성
    public static RewardGrant fromSettlement(
            RewardSettlement settlement,
            CharacterItem characterItem,
            Instant grantedAt
    ) {
        Objects.requireNonNull(
                settlement, "정산 결과는 필수입니다."
        );
        Objects.requireNonNull(
                characterItem, "지급 아이템은 필수입니다."
        );
        Objects.requireNonNull(
                grantedAt, "지급 시각은 필수입니다."
        );

        RewardGrant grant = new RewardGrant();

        grant.user = Objects.requireNonNull(
                settlement.getUser()
        );
        grant.rewardDate = Objects.requireNonNull(
                settlement.getRewardDate()
        );
        grant.completionRate = Objects.requireNonNull(
                settlement.getCompletionRate()
        );

        grant.characterItem = characterItem;
        grant.rewardGrade = Objects.requireNonNull(
                characterItem.getGrade()
        );
        grant.grantedAt = grantedAt;

        return grant;
    }
}
