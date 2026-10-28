
package com.swu.atch.todo.domain;

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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

// 사용자 투두
@Getter
@Entity
@Table(name = "todos")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Todo {

    private static final ZoneId SERVICE_ZONE =
            ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 투두 소유 사용자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 반복 일정 규칙 ID (V10의 사용자 소유권 복합 FK)
    @Column(name = "recurrence_rule_id")
    private Long recurrenceRuleId;

    // 반복 회차의 최초 예정 시작 시각 (생성 후 불변)
    @Column(name = "recurrence_occurrence_at", updatable = false)
    private Instant recurrenceOccurrenceAt;

    // 반복 회차의 기준 날짜 (DAILY: 해당 날짜, WEEKLY: 해당 주 월요일)
    @Column(name = "recurrence_period_start_date", updatable = false)
    private LocalDate recurrencePeriodStartDate;

    // 투두 기본 정보
    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "source_task_name", length = 200)
    private String sourceTaskName;

    @Column(columnDefinition = "text")
    private String memo;

    // 예정 시작 및 마감 일시
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    // 현재 캘린더에 배정된 날짜
    @Column(name = "current_schedule_date", nullable = false)
    private LocalDate currentScheduleDate;

    // 하루종일 일정 여부
    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    // 사용자 예상 소요 시간
    @Column(
            name = "user_estimated_duration_minutes",
            nullable = false
    )
    private Integer userEstimatedDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "user_estimate_source",
            nullable = false,
            length = 30
    )
    private UserEstimateSource userEstimateSource;

    // AI 예상 소요 시간
    @Column(name = "ai_estimated_duration_minutes")
    private Integer aiEstimatedDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "selected_estimate_type",
            nullable = false,
            length = 20
    )
    private SelectedEstimateType selectedEstimateType;

    // 투두 진행 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TodoStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

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

    // 논리 삭제 시각
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // 투두 생성
    @Builder
    private Todo(
            User user,
            Long recurrenceRuleId,
            Instant recurrenceOccurrenceAt,
            LocalDate recurrencePeriodStartDate,
            String title,
            String sourceTaskName,
            String memo,
            Instant startAt,
            Instant dueAt,
            LocalDate currentScheduleDate,
            boolean allDay,
            Integer userEstimatedDurationMinutes,
            UserEstimateSource userEstimateSource,
            Integer aiEstimatedDurationMinutes,
            SelectedEstimateType selectedEstimateType
    ) {
        Objects.requireNonNull(user, "사용자는 필수입니다.");
        Objects.requireNonNull(startAt, "시작 일시는 필수입니다.");
        Objects.requireNonNull(dueAt, "마감 일시는 필수입니다.");
        Objects.requireNonNull(
                currentScheduleDate, "일정 날짜는 필수입니다."
        );
        Objects.requireNonNull(
                userEstimateSource, "예상 시간 출처는 필수입니다."
        );
        Objects.requireNonNull(
                selectedEstimateType, "예상 시간 선택값은 필수입니다."
        );

        if (title == null || title.isBlank()
                || title.length() > 200) {
            throw new IllegalArgumentException(
                    "투두 이름은 1~200자의 공백이 아닌 문자열이어야 합니다."
            );
        }

        if (sourceTaskName != null
                && sourceTaskName.length() > 200) {
            throw new IllegalArgumentException(
                    "원본 작업명은 200자를 초과할 수 없습니다."
            );
        }

        if (!dueAt.isAfter(startAt)) {
            throw new IllegalArgumentException(
                    "마감 일시는 시작 일시보다 이후여야 합니다."
            );
        }

        if (userEstimatedDurationMinutes == null
                || userEstimatedDurationMinutes <= 0) {
            throw new IllegalArgumentException(
                    "사용자 예상 시간은 0분보다 커야 합니다."
            );
        }

        if (aiEstimatedDurationMinutes != null
                && aiEstimatedDurationMinutes <= 0) {
            throw new IllegalArgumentException(
                    "AI 예상 시간은 0분보다 커야 합니다."
            );
        }

        if (selectedEstimateType == SelectedEstimateType.AI
                && aiEstimatedDurationMinutes == null) {
            throw new IllegalArgumentException(
                    "AI 예상 시간을 선택하려면 예측값이 필요합니다."
            );
        }

        // 반복 Todo의 규칙 ID와 두 회차 식별값은 함께 존재해야 함
        boolean recurring = recurrenceRuleId != null;

        if (recurring != (recurrenceOccurrenceAt != null)
                || recurring != (recurrencePeriodStartDate != null)) {
            throw new IllegalArgumentException(
                    "반복 규칙 ID와 회차 식별값은 함께 지정해야 합니다."
            );
        }

        // 하루종일 일정은 서울 기준 당일 00:00~다음 날 00:00
        if (allDay) {
            var start = startAt.atZone(SERVICE_ZONE);
            var end = dueAt.atZone(SERVICE_ZONE);

            if (!start.toLocalTime().equals(LocalTime.MIDNIGHT)
                    || !end.toLocalTime().equals(LocalTime.MIDNIGHT)
                    || !end.toLocalDate().equals(
                    start.toLocalDate().plusDays(1))
                    || !currentScheduleDate.equals(
                    start.toLocalDate())) {
                throw new IllegalArgumentException(
                        "하루종일 일정의 시작·종료 경계가 올바르지 않습니다."
                );
            }
        }

        this.user = user;
        this.recurrenceRuleId = recurrenceRuleId;
        this.recurrenceOccurrenceAt = recurrenceOccurrenceAt;
        this.recurrencePeriodStartDate = recurrencePeriodStartDate;
        this.title = title.trim();
        this.sourceTaskName = sourceTaskName;
        this.memo = memo;
        this.startAt = startAt;
        this.dueAt = dueAt;
        this.currentScheduleDate = currentScheduleDate;
        this.allDay = allDay;
        this.userEstimatedDurationMinutes =
                userEstimatedDurationMinutes;
        this.userEstimateSource = userEstimateSource;
        this.aiEstimatedDurationMinutes =
                aiEstimatedDurationMinutes;
        this.selectedEstimateType = selectedEstimateType;

        // 신규 투두는 진행 전 상태로 시작
        this.status = TodoStatus.PENDING;
    }

    // 편집 가능한 값만 변경하고 반복 회차·원본 작업명·과거 이력은 유지한다.
    public void updateDetails(String title, String memo, Instant startAt, Instant dueAt,
                              boolean allDay, Integer userEstimatedDurationMinutes) {
        requireActive();
        if (status == TodoStatus.IN_PROGRESS || status == TodoStatus.COMPLETED) {
            throw new IllegalStateException("진행 중이거나 완료된 Todo는 바로 수정할 수 없습니다.");
        }
        Objects.requireNonNull(startAt, "시작 일시는 필수입니다.");
        Objects.requireNonNull(dueAt, "마감 일시는 필수입니다.");
        if (title == null || title.isBlank() || title.length() > 200
                || userEstimatedDurationMinutes == null || userEstimatedDurationMinutes <= 0
                || !dueAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Todo 편집 값이 올바르지 않습니다.");
        }
        if (allDay && (!startAt.atZone(SERVICE_ZONE).toLocalTime().equals(LocalTime.MIDNIGHT)
                || !dueAt.atZone(SERVICE_ZONE).toLocalTime().equals(LocalTime.MIDNIGHT)
                || !dueAt.atZone(SERVICE_ZONE).toLocalDate()
                .equals(startAt.atZone(SERVICE_ZONE).toLocalDate().plusDays(1)))) {
            throw new IllegalArgumentException("하루종일 일정의 시작·종료 경계가 올바르지 않습니다.");
        }

        String normalizedTitle = title.trim();
        boolean durationChanged = !Objects.equals(this.userEstimatedDurationMinutes, userEstimatedDurationMinutes);
        boolean predictionInputChanged = !Objects.equals(this.title, normalizedTitle)
                || !Objects.equals(this.memo, memo) || durationChanged;

        // 생성 시의 배정 날짜 검증과 구분한다. 메모 수정으로 이월 날짜를 되돌리지 않는다.
        LocalDate newStartDate = startAt.atZone(SERVICE_ZONE).toLocalDate();
        if (!this.startAt.atZone(SERVICE_ZONE).toLocalDate().equals(newStartDate)) {
            this.currentScheduleDate = newStartDate;
        }
        if (durationChanged) this.userEstimateSource = UserEstimateSource.USER_INPUT;
        if (predictionInputChanged) {
            this.aiEstimatedDurationMinutes = null;
            this.selectedEstimateType = SelectedEstimateType.USER;
        }
        this.title = normalizedTitle;
        this.memo = memo;
        this.startAt = startAt;
        this.dueAt = dueAt;
        this.allDay = allDay;
        this.userEstimatedDurationMinutes = userEstimatedDurationMinutes;
    }

    // 서버가 소유권·입력·제안 토큰을 검증한 뒤 선택값만 적용한다.
    public void applyEstimateChoice(Integer aiMinutes, SelectedEstimateType type) {
        requireActive();
        if (status == TodoStatus.IN_PROGRESS || status == TodoStatus.COMPLETED
                || type == null || (aiMinutes != null && aiMinutes <= 0)
                || (type == SelectedEstimateType.AI && aiMinutes == null)) {
            throw new IllegalArgumentException("예상시간 선택 상태가 올바르지 않습니다.");
        }
        aiEstimatedDurationMinutes = aiMinutes;
        selectedEstimateType = type;
        userEstimateSource = UserEstimateSource.USER_INPUT;
    }

    // 반복 완료는 최초 완료 시각을 보존한다. Session 종료는 Service에서 함께 처리한다.
    public void complete(Instant completedAt) {
        requireActive();
        Objects.requireNonNull(completedAt, "완료 시각은 필수입니다.");
        if (status == TodoStatus.COMPLETED) return;
        this.status = TodoStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    // 완료 취소는 현재 상태만 변경하고 실제 집중시간·일정·이력을 지우지 않는다.
    public void reopen() {
        requireActive();
        if (status != TodoStatus.COMPLETED) return;
        this.status = TodoStatus.PENDING;
        this.completedAt = null;
    }

    // 삭제된 회차도 보존하므로 물리 삭제나 반복 식별값 초기화를 하지 않는다.
    public void softDelete(Instant deletedAt) {
        Objects.requireNonNull(deletedAt, "삭제 시각은 필수입니다.");
        if (this.deletedAt != null) return;
        if (status == TodoStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 Todo는 바로 삭제할 수 없습니다.");
        }
        this.deletedAt = deletedAt;
    }

    // 시작·재개는 새 Session 생성과 같은 Service 트랜잭션에서 수행한다.
    public void start() {
        requireActive();
        if (status != TodoStatus.PENDING && status != TodoStatus.PAUSED)
            throw new IllegalStateException("시작 가능한 Todo가 아닙니다.");
        status = TodoStatus.IN_PROGRESS;
    }

    public void pause() {
        requireActive();
        if (status != TodoStatus.IN_PROGRESS) throw new IllegalStateException("실행 중인 Todo가 아닙니다.");
        status = TodoStatus.PAUSED;
    }

    // 원래 일정·반복 회차 식별값은 유지하고 현재 배정 날짜만 앞으로 이동한다.
    public void rollOverTo(LocalDate date) {
        requireActive();
        java.util.Objects.requireNonNull(date);
        if (status == TodoStatus.COMPLETED || date.isBefore(currentScheduleDate))
            throw new IllegalStateException("이월 가능한 날짜·상태가 아닙니다.");
        currentScheduleDate = date;
    }

    private void requireActive() {
        if (deletedAt != null) throw new IllegalStateException("삭제된 Todo는 변경할 수 없습니다.");
    }
}
