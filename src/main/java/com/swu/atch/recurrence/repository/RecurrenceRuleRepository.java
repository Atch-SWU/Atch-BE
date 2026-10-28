package com.swu.atch.recurrence.repository;

import com.swu.atch.recurrence.domain.RecurrenceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 사용자 반복 일정 규칙 데이터 접근
public interface RecurrenceRuleRepository
        extends JpaRepository<RecurrenceRule, Long> {

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
