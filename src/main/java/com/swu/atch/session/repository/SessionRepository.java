package com.swu.atch.session.repository;

import com.swu.atch.session.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

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
