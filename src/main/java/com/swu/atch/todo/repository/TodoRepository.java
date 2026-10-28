package com.swu.atch.todo.repository;

import com.swu.atch.todo.domain.Todo;
import com.swu.atch.todo.domain.TodoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

// 사용자 투두 데이터 접근
public interface TodoRepository extends JpaRepository<Todo, Long> {

    List<Todo> findByUser_IdAndDeletedAtIsNullAndCurrentScheduleDateBeforeAndStatusNot(
            Long userId, LocalDate date, TodoStatus status);
    List<Todo> findByUser_IdAndDeletedAtIsNullAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDescIdDesc(
            Long userId, TodoStatus status, java.time.Instant from, java.time.Instant to);
    Page<Todo> findByUser_IdAndDeletedAtIsNullAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            Long userId, TodoStatus status, java.time.Instant from, java.time.Instant to, Pageable pageable);
    List<Todo> findTop3ByUser_IdAndDeletedAtIsNullAndStatusInOrderByCurrentScheduleDateAscStartAtAscIdAsc(
            Long userId, Collection<TodoStatus> statuses);

    // User 잠금 이후 호출한다. 반복 삭제의 멱등 처리를 위해 삭제된 본인 행도 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Todo t where t.id = :id and t.user.id = :userId")
    Optional<Todo> findOwnedByIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    // 상태를 먼저 필터링한 뒤 페이지를 나눈다. 정렬은 Service의 고정 Pageable을 사용한다.
    Page<Todo> findAllByUser_IdAndCurrentScheduleDateBetweenAndStatusInAndDeletedAtIsNull(
            Long userId, LocalDate from, LocalDate to, Collection<TodoStatus> statuses, Pageable pageable);

    @Query("""
            select distinct t.currentScheduleDate from Todo t
            where t.user.id = :userId and t.deletedAt is null
              and t.currentScheduleDate between :from and :to
            order by t.currentScheduleDate
            """)
    List<LocalDate> findActiveScheduleDates(@Param("userId") Long userId,
                                          @Param("from") LocalDate from, @Param("to") LocalDate to);

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
