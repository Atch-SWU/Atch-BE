package com.swu.atch.reward.repository;

import org.springframework.data.jpa.repository.EntityGraph;

import com.swu.atch.reward.domain.ItemGrade;
import com.swu.atch.reward.domain.RewardGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

// 일일 아이템 지급 이력 조회
public interface RewardGrantRepository
        extends JpaRepository<RewardGrant, Long> {

    // 날짜와 등급별 지급 이력 존재 여부
    boolean existsByUser_IdAndRewardDateAndRewardGrade(
            Long userId,
            LocalDate rewardDate,
            ItemGrade rewardGrade
    );

    // 특정 날짜의 실제 지급 결과
    List<RewardGrant>
    findAllByUser_IdAndRewardDateOrderByRewardGradeAsc(
            Long userId,
            LocalDate rewardDate
    );

    // 페이지 내 날짜들의 실제 지급과 아이템을 한 번에 로딩한다. B/A/S 정렬은 응답 변환에서 수행한다.
    @EntityGraph(attributePaths = "characterItem")
    List<RewardGrant> findAllByUser_IdAndRewardDateInOrderByRewardDateDescIdAsc(
            Long userId, List<LocalDate> rewardDates);

}
