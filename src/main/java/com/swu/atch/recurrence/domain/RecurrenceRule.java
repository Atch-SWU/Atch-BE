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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;

// 사용자가 등록한 반복 일정 규칙
@Getter
@Entity
@Table(name = "recurrence_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecurrenceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 규칙 소유 사용자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    // 규칙 생성에 사용한 반복 패턴
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "source_pattern_id",
            nullable = false,
            unique = true,
            updatable = false
    )
    private RepeatPattern sourcePattern;

    @Column(name = "task_name", nullable = false, length = 200)
    private String taskName;

    @Column(columnDefinition = "text")
    private String memo;

    // 등록 시 확정되는 반복 주기
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private RecurrenceFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "scheduled_time", nullable = false)
    private LocalTime scheduledTime;

    @Column(
            name = "default_deadline_offset_minutes",
            nullable = false
    )
    private Integer defaultDeadlineOffsetMinutes;

    @Column(
            name = "default_user_estimated_duration_minutes",
            nullable = false
    )
    private Integer defaultUserEstimatedDurationMinutes;

    // 개별 반복 규칙의 활성화 여부
    @Column(nullable = false)
    private boolean enabled;

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

    // 사용자에 의한 규칙 논리 삭제
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // 감지한 패턴을 바탕으로 반복 규칙 등록
    @Builder
    private RecurrenceRule(
            User user,
            RepeatPattern sourcePattern,
            String taskName,
            String memo,
            DayOfWeek dayOfWeek,
            LocalTime scheduledTime,
            Integer defaultDeadlineOffsetMinutes,
            Integer defaultUserEstimatedDurationMinutes
    ) {
        Objects.requireNonNull(user, "사용자는 필수입니다.");
        Objects.requireNonNull(
                sourcePattern, "원본 반복 패턴은 필수입니다."
        );
        Objects.requireNonNull(
                scheduledTime, "반복 예정 시각은 필수입니다."
        );

        RecurrenceFrequency frequency =
                Objects.requireNonNull(
                        sourcePattern.getFrequency(),
                        "원본 반복 주기는 필수입니다."
                );

        User patternOwner = Objects.requireNonNull(
                sourcePattern.getUser(),
                "원본 패턴의 사용자는 필수입니다."
        );

        // 등록 규칙과 원본 패턴은 같은 사용자에게 속해야 함
        if (user != patternOwner
                && (user.getId() == null
                || !user.getId().equals(patternOwner.getId()))) {
            throw new IllegalArgumentException(
                    "다른 사용자의 반복 패턴으로 규칙을 생성할 수 없습니다."
            );
        }

        if (taskName == null || taskName.isBlank()
                || taskName.length() > 200) {
            throw new IllegalArgumentException(
                    "반복 작업명은 1~200자의 공백이 아닌 문자열이어야 합니다."
            );
        }

        if (defaultDeadlineOffsetMinutes == null
                || defaultDeadlineOffsetMinutes <= 0) {
            throw new IllegalArgumentException(
                    "기본 마감 간격은 0분보다 커야 합니다."
            );
        }

        if (defaultUserEstimatedDurationMinutes == null
                || defaultUserEstimatedDurationMinutes <= 0) {
            throw new IllegalArgumentException(
                    "기본 예상 소요시간은 0분보다 커야 합니다."
            );
        }

        validateDayOfWeek(frequency, dayOfWeek);

        this.user = user;
        this.sourcePattern = sourcePattern;
        this.taskName = taskName.trim();
        this.memo = memo;
        this.frequency = frequency;
        this.dayOfWeek = dayOfWeek;
        this.scheduledTime = scheduledTime;
        this.defaultDeadlineOffsetMinutes =
                defaultDeadlineOffsetMinutes;
        this.defaultUserEstimatedDurationMinutes =
                defaultUserEstimatedDurationMinutes;
        this.enabled = true;
    }

    // 반복 주기에 따른 요일 검증
    private static void validateDayOfWeek(
            RecurrenceFrequency frequency,
            DayOfWeek dayOfWeek
    ) {
        if (frequency == RecurrenceFrequency.DAILY
                && dayOfWeek != null) {
            throw new IllegalArgumentException(
                    "매일 반복 규칙에는 요일을 지정할 수 없습니다."
            );
        }

        if (frequency == RecurrenceFrequency.WEEKLY
                && dayOfWeek == null) {
            throw new IllegalArgumentException(
                    "매주 반복 규칙에는 요일이 필요합니다."
            );
        }
    }
}
