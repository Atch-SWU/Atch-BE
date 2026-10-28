package com.swu.atch.session.repository;

import com.swu.atch.session.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findFirstByTodo_User_IdOrderByStartedAtDescIdDesc(Long userId);
    Optional<Session> findFirstByTodo_IdAndTodo_User_IdOrderByStartedAtDescIdDesc(Long todoId, Long userId);
    org.springframework.data.domain.Page<Session> findByTodo_IdAndTodo_User_Id(
            Long todoId, Long userId, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select s from Session s join fetch s.todo t
            where t.user.id = :userId and s.startedAt < :to
              and (s.endedAt is null or s.endedAt >= :from)
            order by s.startedAt, s.id
            """)
    List<Session> findOverlapping(@Param("userId") Long userId,
            @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    // 기간 필터 이전에 이전 구간을 계산해야 기간 첫 재개도 올바르게 집계된다.
    @Query(value = """
            select count(*) from (
              select s.started_at, s.resume_counted, lag(s.end_reason) over(partition by s.todo_id order by s.started_at,s.id) previous_reason
              from sessions s join todos t on t.id=s.todo_id where t.user_id=:userId
            ) history where started_at >= :from and started_at < :to
              and coalesce(resume_counted, previous_reason in ('PAUSED','SWITCHED'))
            """, nativeQuery = true)
    long countResumes(@Param("userId") Long userId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    // 목록의 Todo마다 합계 쿼리를 반복하지 않고 현재 페이지의 종료 구간만 일괄 집계한다.
    @Query("""
            select s.todo.id as todoId, sum(s.durationSeconds) as durationSeconds
            from Session s
            where s.todo.user.id = :userId and s.todo.id in :todoIds and s.endedAt is not null
            group by s.todo.id
            """)
    List<DurationTotal> sumClosedDurationSecondsByTodoIdsAndUserId(
            @Param("todoIds") Collection<Long> todoIds, @Param("userId") Long userId);

    interface DurationTotal {
        Long getTodoId();
        Long getDurationSeconds();
    }

    // 사용자의 특정 Todo에 속한 전체 Session 조회
    List<Session> findByTodo_IdAndTodo_User_IdOrderByStartedAtAscIdAsc(
            Long todoId,
            Long userId
    );

    // 사용자의 특정 Todo에서 현재 열려 있는 Session 조회
    Optional<Session> findByTodo_IdAndTodo_User_IdAndEndedAtIsNull(
            Long todoId,
            Long userId
    );

    // 사용자가 현재 실행 중인 모든 Session 조회
    List<Session> findByTodo_User_IdAndEndedAtIsNull(Long userId);

    // Todo의 실제 집중 시간 합계(초)
    @Query("""
            SELECT COALESCE(SUM(s.durationSeconds), 0L)
            FROM Session s
            WHERE s.todo.id = :todoId
              AND s.todo.user.id = :userId
              AND s.endedAt IS NOT NULL
            """)
    long sumDurationSecondsByTodoIdAndUserId(
            @Param("todoId") Long todoId,
            @Param("userId") Long userId
    );
}
