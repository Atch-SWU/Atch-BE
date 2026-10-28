package com.swu.atch.reward.repository;

import com.swu.atch.reward.domain.UserCharacterItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 사용자 보유 아이템 조회
public interface UserCharacterItemRepository
        extends JpaRepository<UserCharacterItem, Long> {

    // 사용자별 최근 획득 아이템 목록
    List<UserCharacterItem>
    findAllByUser_IdOrderByAcquiredAtDescIdDesc(
            Long userId
    );

    // 사용자 아이템 소유 여부
    boolean existsByUser_IdAndCharacterItem_Id(
            Long userId,
            Long characterItemId
    );

    // 사용자가 보유한 활성 아이템 수
    long countByUser_IdAndCharacterItem_ActiveTrue(
            Long userId
    );
}
