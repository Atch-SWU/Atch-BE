package com.swu.atch.reward.repository;

import com.swu.atch.reward.domain.ItemCategory;
import com.swu.atch.reward.domain.UserEquippedItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 사용자 장착 아이템 조회
public interface UserEquippedItemRepository
        extends JpaRepository<UserEquippedItem, Long> {

    // 사용자별 현재 장착 상태
    List<UserEquippedItem> findAllByUser_IdOrderByCategoryAsc(
            Long userId
    );

    // 특정 카테고리의 현재 장착 아이템
    Optional<UserEquippedItem> findByUser_IdAndCategory(
            Long userId,
            ItemCategory category
    );
}
