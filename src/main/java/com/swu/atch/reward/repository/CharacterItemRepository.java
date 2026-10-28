package com.swu.atch.reward.repository;

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
}
