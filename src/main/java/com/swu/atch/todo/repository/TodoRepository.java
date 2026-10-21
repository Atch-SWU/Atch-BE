package com.swu.atch.todo.repository;

import com.swu.atch.todo.domain.Todo;
import com.swu.atch.todo.domain.TodoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// 사용자 투두 데이터 접근
public interface TodoRepository extends JpaRepository<Todo, Long> {

    // 사용자 소유의 삭제되지 않은 투두 단건 조회
    Optional<Todo> findByIdAndUser_IdAndDeletedAtIsNull(
            Long id,
            Long userId
    );

    // 특정 날짜에 배정된 투두 목록 조회
    List<Todo>
    findAllByUser_IdAndCurrentScheduleDateAndDeletedAtIsNullOrderByStartAtAscIdAsc(
            Long userId,
            LocalDate scheduleDate
    );

    // 날짜 범위에 배정된 투두 목록 조회
    Page<Todo>
    findAllByUser_IdAndCurrentScheduleDateBetweenAndDeletedAtIsNull(
            Long userId,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    );

    // 특정 날짜에 투두가 존재하는지 확인
    boolean existsByUser_IdAndCurrentScheduleDateAndDeletedAtIsNull(
            Long userId,
            LocalDate scheduleDate
    );

    // 사용자별 특정 상태의 투두 목록 조회
    List<Todo>
    findAllByUser_IdAndStatusAndDeletedAtIsNullOrderByStartAtAscIdAsc(
            Long userId,
            TodoStatus status
    );
}
