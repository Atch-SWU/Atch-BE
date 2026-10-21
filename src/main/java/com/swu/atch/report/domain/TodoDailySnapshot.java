package com.swu.atch.report.domain;

import com.swu.atch.todo.domain.Todo;
import com.swu.atch.todo.domain.TodoStatus;
import com.swu.atch.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(
        name = "todo_daily_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_todo_daily_snapshots_todo_date",
                columnNames = {"todo_id", "schedule_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoDailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "todo_id", nullable = false)
    private Todo todo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "schedule_date", nullable = false)
    private LocalDate scheduleDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_at_day_end", nullable = false, length = 30)
    private TodoStatus statusAtDayEnd;

    @Column(name = "start_at_snapshot", nullable = false)
    private Instant startAtSnapshot;

    @Column(name = "due_at_snapshot", nullable = false)
    private Instant dueAtSnapshot;

    @Column(name = "completed_at_snapshot")
    private Instant completedAtSnapshot;

    @Column(name = "was_rolled_over", nullable = false)
    private boolean wasRolledOver;

    @Column(name = "was_overdue", nullable = false)
    private boolean wasOverdue;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static TodoDailySnapshot capture(
            Todo todo,
            LocalDate scheduleDate,
            boolean wasRolledOver,
            boolean wasOverdue,
            Instant snapshotAt
    ) {
        Objects.requireNonNull(todo, "Todo는 필수입니다.");
        Objects.requireNonNull(scheduleDate, "기록 날짜는 필수입니다.");
        Objects.requireNonNull(snapshotAt, "기록 시각은 필수입니다.");

        if (todo.getId() == null) {
            throw new IllegalArgumentException(
                    "저장된 Todo에 대해서만 Snapshot을 생성할 수 있습니다."
            );
        }

        TodoDailySnapshot snapshot = new TodoDailySnapshot();

        snapshot.todo = todo;
        snapshot.user = Objects.requireNonNull(todo.getUser());
        snapshot.scheduleDate = scheduleDate;

        snapshot.statusAtDayEnd =
                Objects.requireNonNull(todo.getStatus());

        snapshot.startAtSnapshot =
                Objects.requireNonNull(todo.getStartAt());

        snapshot.dueAtSnapshot =
                Objects.requireNonNull(todo.getDueAt());

        snapshot.completedAtSnapshot = todo.getCompletedAt();

        snapshot.wasRolledOver = wasRolledOver;
        snapshot.wasOverdue = wasOverdue;
        snapshot.snapshotAt = snapshotAt;

        return snapshot;
    }
}