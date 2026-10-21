package com.swu.atch.report.repository;

import com.swu.atch.report.domain.TodoDailySnapshot;
import com.swu.atch.todo.domain.TodoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TodoDailySnapshotRepository
        extends JpaRepository<TodoDailySnapshot, Long> {

    // 특정 사용자의 특정 Todo·날짜 Snapshot 조회
    Optional<TodoDailySnapshot>
    findByTodo_IdAndUser_IdAndScheduleDate(
            Long todoId,
            Long userId,
            LocalDate scheduleDate
    );

    // 특정 Todo·날짜의 Snapshot 존재 여부
    boolean existsByTodo_IdAndScheduleDate(
            Long todoId,
            LocalDate scheduleDate
    );

    // 사용자별 특정 날짜의 Snapshot 목록
    List<TodoDailySnapshot>
    findByUser_IdAndScheduleDateOrderByIdAsc(
            Long userId,
            LocalDate scheduleDate
    );

    // 사용자별 날짜 범위 Snapshot 목록
    List<TodoDailySnapshot>
    findByUser_IdAndScheduleDateBetweenOrderByScheduleDateAscIdAsc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    // 특정 날짜에 해당 상태로 종료된 Todo 수
    long countByUser_IdAndScheduleDateAndStatusAtDayEnd(
            Long userId,
            LocalDate scheduleDate,
            TodoStatus statusAtDayEnd
    );
}