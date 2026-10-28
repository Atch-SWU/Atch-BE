package com.swu.atch.session.domain;

import com.swu.atch.todo.domain.Todo;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "todo_id", nullable = false)
    private Todo todo;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private SessionEndReason endReason;

    // 기존 행은 null(과거 종료 사유로 추정), 신규 시작=false·실제 재개=true다.
    @Column(name = "resume_counted")
    private Boolean resumeCounted;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 새로운 집중 구간 시작
    public static Session start(Todo todo, Instant startedAt) {
        Objects.requireNonNull(todo, "Todo는 필수입니다.");
        Objects.requireNonNull(startedAt, "시작 시각은 필수입니다.");

        Session session = new Session();
        session.todo = todo;
        session.startedAt = startedAt;
        session.resumeCounted = false;

        return session;
    }

    // 완료 취소 후 일반 시작과 구분하여 일시정지·전환 뒤 실제 재개만 집계한다.
    public static Session resume(Todo todo, Instant startedAt) {
        var session = start(todo, startedAt);
        session.resumeCounted = true;
        return session;
    }

    // 집중 구간 종료
    public void finish(Instant endedAt, SessionEndReason endReason) {
        Objects.requireNonNull(endedAt, "종료 시각은 필수입니다.");
        Objects.requireNonNull(endReason, "종료 사유는 필수입니다.");

        if (this.endedAt != null) {
            throw new IllegalStateException("이미 종료된 Session입니다.");
        }

        if (endedAt.isBefore(this.startedAt)) {
            throw new IllegalArgumentException(
                    "종료 시각은 시작 시각보다 빠를 수 없습니다."
            );
        }

        long seconds = Duration.between(this.startedAt, endedAt).getSeconds();

        if (seconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Session 시간이 허용 범위를 초과했습니다.");
        }

        this.endedAt = endedAt;
        this.durationSeconds = (int) seconds;
        this.endReason = endReason;
    }
}