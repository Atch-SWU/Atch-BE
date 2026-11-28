package com.swu.atch.reward.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swu.atch.reward.domain.CharacterItem;
import com.swu.atch.reward.domain.ItemCategory;
import com.swu.atch.reward.domain.ItemGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Collection;

// 캐릭터 아이템 카탈로그 조회
public interface CharacterItemRepository
        extends JpaRepository<CharacterItem, Long> {

    List<CharacterItem> findAllByItemCodeIn(Collection<String> itemCodes);

    // DB 커밋 결과가 불확실할 때 실제 참조 여부를 확인한 뒤 임시 이미지를 정리한다.
    List<CharacterItem> findAllByAssetKeyIn(Collection<String> assetKeys);

    interface CatalogRow {
        CharacterItem getItem();
        boolean getOwned();
        Instant getAcquiredAt();
    }

    // 사용자별 보유 관계는 UNIQUE이므로 아이템마다 한 행으로 페이지와 개수를 계산한다.
    @Query(value = """
            select item as item, case when owned.id is not null then true else false end as owned,
                   owned.acquiredAt as acquiredAt
            from CharacterItem item
            left join UserCharacterItem owned on owned.characterItem=item and owned.user.id=:userId
            where (item.active=true or owned.id is not null)
              and (:category is null or item.category=:category)
              and (:grade is null or item.grade=:grade)
            """, countQuery = """
            select count(item) from CharacterItem item
            left join UserCharacterItem owned on owned.characterItem=item and owned.user.id=:userId
            where (item.active=true or owned.id is not null)
              and (:category is null or item.category=:category)
              and (:grade is null or item.grade=:grade)
            """)
    Page<CatalogRow> findCatalog(@Param("userId") Long userId, @Param("category") ItemCategory category,
                                @Param("grade") ItemGrade grade, Pageable pageable);

    // 특정 등급의 지급 가능한 아이템 목록
    List<CharacterItem> findAllByGradeAndActiveTrueOrderByIdAsc(
            ItemGrade grade
    );

    // 전체 수집 완료 판단을 위한 활성 아이템 수
    long countByActiveTrue();

    @Query("""
            select distinct item.grade from CharacterItem item
            where item.active=true
              and not exists (select owned.id from UserCharacterItem owned
                              where owned.user.id=:userId and owned.characterItem=item)
            """)
    List<ItemGrade> findGradesWithActiveUnownedItems(@Param("userId") Long userId);

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
