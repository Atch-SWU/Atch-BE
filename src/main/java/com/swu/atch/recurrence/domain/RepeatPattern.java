package com.swu.atch.recurrence.domain;

import com.swu.atch.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

// 사용자의 투두에서 감지된 반복 패턴
@Getter
@Entity
@Table(name = "repeat_patterns")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepeatPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 패턴 소유 사용자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecurrenceFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;

    // 하루종일은 시각 대신 날짜로 반복을 구분한다.
    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    // 초기화 이전 Todo를 재사용하지 않는 사용자별 관측 경계다. FK가 아니다.
    @Column(name = "detection_after_todo_id", nullable = false)
    private long detectionAfterTodoId;

    @Column(name = "occurrence_count", nullable = false)
    private Integer occurrenceCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RepeatPatternStatus status;

    @Column(name = "last_occurrence_date", nullable = false)
    private LocalDate lastOccurrenceDate;

    @Column(name = "last_suggested_at")
    private Instant lastSuggestedAt;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 신규 반복 후보 생성
    @Builder
    private RepeatPattern(
            User user,
            String taskName,
            RecurrenceFrequency frequency,
            DayOfWeek dayOfWeek,
            LocalTime scheduledTime,
            boolean allDay,
            long detectionAfterTodoId,
            Integer occurrenceCount,
            LocalDate lastOccurrenceDate
    ) {
        Objects.requireNonNull(user, "사용자는 필수입니다.");
        Objects.requireNonNull(frequency, "반복 주기는 필수입니다.");
        validateTime(allDay, scheduledTime);
        if (detectionAfterTodoId < 0) throw new IllegalArgumentException("관측 경계는 음수일 수 없습니다.");
        Objects.requireNonNull(
                lastOccurrenceDate, "마지막 반복 발생일은 필수입니다."
        );

        if (taskName == null || taskName.isBlank()
                || taskName.length() > 200) {
            throw new IllegalArgumentException(
                    "반복 작업명은 1~200자의 공백이 아닌 문자열이어야 합니다."
            );
        }

        if (occurrenceCount == null || occurrenceCount <= 0) {
            throw new IllegalArgumentException(
                    "패턴 감지 횟수는 0보다 커야 합니다."
            );
        }

        validateDayOfWeek(frequency, dayOfWeek);

        this.user = user;
        this.taskName = taskName.trim();
        this.frequency = frequency;
        this.dayOfWeek = dayOfWeek;
        this.scheduledTime = scheduledTime;
        this.allDay = allDay;
        this.detectionAfterTodoId = detectionAfterTodoId;
        this.occurrenceCount = occurrenceCount;
        this.lastOccurrenceDate = lastOccurrenceDate;
        this.status = RepeatPatternStatus.DETECTING;
    }

    // 조회 횟수가 아닌 실제 관측 결과로만 상태를 갱신한다.
    public void observe(int count, LocalDate lastDate, boolean suggest, Instant now) {
        if (status == RepeatPatternStatus.DISMISSED || status == RepeatPatternStatus.REGISTERED) return;
        if (count < 0 || lastDate == null) throw new IllegalArgumentException("관측값이 올바르지 않습니다.");
        occurrenceCount = count;
        lastOccurrenceDate = lastDate;
        var next = suggest && count >= 2 ? RepeatPatternStatus.SUGGESTED : RepeatPatternStatus.DETECTING;
        if (next == RepeatPatternStatus.SUGGESTED && status != next) lastSuggestedAt = Objects.requireNonNull(now);
        status = next;
    }

    public void skip(long afterTodoId) {
        requireSuggested();
        advanceDetectionBoundary(afterTodoId);
        occurrenceCount = 0;
        status = RepeatPatternStatus.DETECTING;
    }

    public void dismiss() { requireSuggested(); status = RepeatPatternStatus.DISMISSED; }
    public void register() { requireSuggested(); status = RepeatPatternStatus.REGISTERED; }

    // 규칙 삭제 시 원본 상태·횟수를 유지한 채 새 관측의 하한만 전진시킨다.
    public void advanceDetectionBoundary(long afterTodoId) {
        if (afterTodoId < 0) throw new IllegalArgumentException("관측 경계는 음수일 수 없습니다.");
        detectionAfterTodoId = Math.max(detectionAfterTodoId, afterTodoId);
    }

    private void requireSuggested() {
        if (status != RepeatPatternStatus.SUGGESTED) throw new IllegalStateException("현재 추천 상태가 아닙니다.");
    }

    private static void validateTime(boolean allDay, LocalTime time) {
        if (allDay ? time != null : time == null || time.getNano() % 1000 != 0)
            throw new IllegalArgumentException("하루종일은 시각 없이, 시각 지정은 유효한 예정 시각이 필요합니다.");
    }

    // 반복 주기에 따른 요일 검증
    private static void validateDayOfWeek(
            RecurrenceFrequency frequency,
            DayOfWeek dayOfWeek
    ) {
        if (frequency == RecurrenceFrequency.DAILY
                && dayOfWeek != null) {
            throw new IllegalArgumentException(
                    "매일 반복 패턴에는 요일을 지정할 수 없습니다."
            );
        }

        if (frequency == RecurrenceFrequency.WEEKLY
                && dayOfWeek == null) {
            throw new IllegalArgumentException(
                    "매주 반복 패턴에는 요일이 필요합니다."
            );
        }
    }
}
