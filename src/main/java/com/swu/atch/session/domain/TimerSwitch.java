package com.swu.atch.session.domain;

import com.swu.atch.todo.domain.Todo;
import com.swu.atch.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

// Session 종료와 전환 예약을 같은 PostgreSQL 트랜잭션으로 보존한다.
@Entity
@Table(name = "timer_switch_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimerSwitch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "request_id", nullable = false, unique = true) private UUID requestId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "source_todo_id", nullable = false) private Todo sourceTodo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "target_todo_id", nullable = false) private Todo targetTodo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "source_session_id", nullable = false) private Session sourceSession;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "started_session_id") private Session startedSession;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TimerSwitchState state;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "ready_at", nullable = false) private Instant readyAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static TimerSwitch request(User user, Todo source, Todo target, Session session,
                                      Instant now, Instant ready, Instant expires) {
        if (!source.getUser().getId().equals(user.getId()) || !target.getUser().getId().equals(user.getId())
                || !session.getTodo().getId().equals(source.getId()) || source.getId().equals(target.getId())
                || ready.isBefore(now) || !expires.isAfter(ready)) throw new IllegalArgumentException("전환 요청이 올바르지 않습니다.");
        var result = new TimerSwitch();
        result.requestId = UUID.randomUUID(); result.user = user; result.sourceTodo = source;
        result.targetTodo = target; result.sourceSession = session; result.requestedAt = now;
        result.readyAt = ready; result.expiresAt = expires; result.state = TimerSwitchState.WAITING;
        return result;
    }

    public void start(Session session) {
        if (state != TimerSwitchState.WAITING || !session.getTodo().getId().equals(targetTodo.getId()))
            throw new IllegalStateException("시작 가능한 전환 요청이 아닙니다.");
        startedSession = session; state = TimerSwitchState.STARTED;
    }
    public void cancel() { if (state == TimerSwitchState.WAITING) state = TimerSwitchState.CANCELLED; }
    public void expire(Instant now) {
        if (state == TimerSwitchState.WAITING && !now.isBefore(expiresAt)) state = TimerSwitchState.EXPIRED;
    }
}
