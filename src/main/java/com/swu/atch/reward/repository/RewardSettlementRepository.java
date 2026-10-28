package com.swu.atch.reward.repository;

import com.swu.atch.reward.domain.RewardSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

// 사용자별 일일 보상 정산 기록 조회
public interface RewardSettlementRepository
        extends JpaRepository<RewardSettlement, Long> {

    // 특정 날짜 정산 결과 조회
    Optional<RewardSettlement> findByUser_IdAndRewardDate(
            Long userId,
            LocalDate rewardDate
    );

    // 정산 중복 여부 확인
    boolean existsByUser_IdAndRewardDate(
            Long userId,
            LocalDate rewardDate
    );
}
