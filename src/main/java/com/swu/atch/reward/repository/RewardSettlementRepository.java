package com.swu.atch.reward.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

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

    Page<RewardSettlement> findAllByUser_IdAndRewardDateBetween(
            Long userId, LocalDate from, LocalDate to, Pageable pageable);

    // untilDate는 일일 확정 커서의 다음 날짜(미포함)다. 최대 32개로 다음 묶음 존재도 확인한다.
    @Query(value = """
            select cast(d.day as date)
            from generate_series(cast(:fromDate as timestamp),
                                 cast(:untilDate as timestamp) - interval '1 day',
                                 interval '1 day') as d(day)
            where not exists (select 1 from reward_settlements r
                              where r.user_id=:userId and r.reward_date=cast(d.day as date))
            order by d.day limit :limit
            """, nativeQuery = true)
    List<LocalDate> findMissingDates(@Param("userId") Long userId, @Param("fromDate") LocalDate fromDate,
                                   @Param("untilDate") LocalDate untilDate, @Param("limit") int limit);

}
