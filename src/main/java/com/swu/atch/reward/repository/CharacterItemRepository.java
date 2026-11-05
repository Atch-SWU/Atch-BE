package com.swu.atch.reward.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swu.atch.reward.domain.CharacterItem;
import com.swu.atch.reward.domain.ItemGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 캐릭터 아이템 카탈로그 조회
public interface CharacterItemRepository
        extends JpaRepository<CharacterItem, Long> {

    // 특정 등급의 지급 가능한 아이템 목록
    List<CharacterItem> findAllByGradeAndActiveTrueOrderByIdAsc(
            ItemGrade grade
    );

    // 전체 수집 완료 판단을 위한 활성 아이템 수
    long countByActiveTrue();

    // 정산 트랜잭션 동안 후보의 신규 지급 가능 상태를 유지한다.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select item from CharacterItem item
            where item.grade=:grade and item.active=true
              and not exists (select owned.id from UserCharacterItem owned
                              where owned.user.id=:userId and owned.characterItem=item)
            order by item.id
            """)
    List<CharacterItem> findActiveUnownedForGrant(@Param("userId") Long userId, @Param("grade") ItemGrade grade);

    interface CollectionCounts {
        long getActiveItemCount();
        long getOwnedActiveItemCount();
    }

    // 두 수를 한 SQL의 동일 스냅샷에서 집계한다.
    @Query(value = """
            select count(*) as "activeItemCount", count(owned.id) as "ownedActiveItemCount"
            from character_items item
            left join user_character_items owned
              on owned.character_item_id=item.id and owned.user_id=:userId
            where item.active=true
            """, nativeQuery = true)
    CollectionCounts collectionCounts(@Param("userId") Long userId);

}
