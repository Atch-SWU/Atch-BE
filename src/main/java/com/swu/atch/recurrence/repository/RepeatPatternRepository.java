package com.swu.atch.recurrence.repository;

import com.swu.atch.recurrence.domain.RepeatPattern;
import com.swu.atch.recurrence.domain.RepeatPatternStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// 사용자 반복 패턴 데이터 접근
public interface RepeatPatternRepository
        extends JpaRepository<RepeatPattern, Long> {

    // 사용자 소유의 반복 패턴 단건 조회
    Optional<RepeatPattern> findByIdAndUser_Id(
            Long id,
            Long userId
    );

    // 사용자별 상태에 따른 반복 패턴 목록 조회
    List<RepeatPattern> findAllByUser_IdAndStatusOrderByLastSuggestedAtDescIdDesc(
            Long userId,
            RepeatPatternStatus status
    );
}
