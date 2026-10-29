package com.swu.atch.recurrence.repository;

import com.swu.atch.recurrence.domain.RecurrenceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 사용자 반복 일정 규칙 데이터 접근
public interface RecurrenceRuleRepository
        extends JpaRepository<RecurrenceRule, Long> {

    org.springframework.data.domain.Page<RecurrenceRule> findAllByUser_IdAndDeletedAtIsNull(
            Long userId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<RecurrenceRule> findAllByUser_IdAndDeletedAtIsNullAndEnabled(
            Long userId, boolean enabled, org.springframework.data.domain.Pageable pageable);

    Optional<RecurrenceRule> findByIdAndUser_Id(Long id, Long userId);
    Optional<RecurrenceRule> findBySourcePattern_IdAndUser_Id(Long patternId, Long userId);
    List<RecurrenceRule> findAllByUser_IdOrderByIdAsc(Long userId);

    // 커서 단위로 사용자만 가져오며 추천 설정과는 무관하게 등록 규칙을 처리한다.
    @org.springframework.data.jpa.repository.Query(value = """
            select distinct user_id from recurrence_rules
            where user_id > :afterId and enabled=true and deleted_at is null
            order by user_id limit 100
            """, nativeQuery = true)
    List<Long> findGenerationUserIds(@org.springframework.data.repository.query.Param("afterId") long afterId);

    // 사용자 소유의 삭제되지 않은 규칙 단건 조회
    Optional<RecurrenceRule> findByIdAndUser_IdAndDeletedAtIsNull(
            Long id,
            Long userId
    );

    // 사용자별 삭제되지 않은 반복 규칙 목록
    List<RecurrenceRule>
    findAllByUser_IdAndDeletedAtIsNullOrderByIdDesc(
            Long userId
    );

    // 사용자별 활성화된 반복 규칙 목록
    List<RecurrenceRule>
    findAllByUser_IdAndEnabledTrueAndDeletedAtIsNullOrderByIdAsc(
            Long userId
    );

    // 원본 패턴으로 등록된 규칙의 존재 여부
    boolean existsBySourcePattern_Id(Long sourcePatternId);
}
