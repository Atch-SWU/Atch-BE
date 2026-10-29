package com.swu.atch.recurrence.service;

import com.swu.atch.ai.service.*;
import com.swu.atch.common.exception.*;
import com.swu.atch.recurrence.domain.RecurrenceFrequency;
import com.swu.atch.recurrence.repository.RecurrenceRuleRepository;
import com.swu.atch.report.service.DailySettlementService;
import com.swu.atch.todo.domain.*;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import static com.swu.atch.recurrence.service.RecurrencePolicy.*;

@Service
@RequiredArgsConstructor
public class RecurrenceGenerationService {
    private final UserRepository users;
    private final DailySettlementService settlements;
    private final org.springframework.transaction.PlatformTransactionManager transactions;
    private final RecurrenceRuleRepository rules;
    private final TodoRepository todos;
    private final ProfileService profiles;
    private final AiDraftService drafts;

    // 스케줄러 진입점. 사용자별 실패와 트랜잭션을 분리한다.
    public int prepare(Long userId) {
        // 이미 업무 트랜잭션이 있으면 참여하고, 스케줄러라면 확정 묶음의 진행을 먼저 커밋한다.
        settlements.requirePrepared(userId, null);
        return new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> {
            var user = users.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
            Instant now = settlements.requireCurrentLocked(user);
            return generateTodayLocked(user, now);
        });
    }

    // Todo 조회의 기존 User 잠금을 그대로 사용한다. 별도 트랜잭션으로 같은 행을 다시 잠그지 않는다.
    @Transactional(propagation = Propagation.MANDATORY)
    public int generateTodayLocked(User user, Instant now) {
        try { return generate(user, now); }
        catch (org.springframework.dao.DataAccessException ex) {
            throw new BusinessException(ErrorCode.RECURRENCE_PREPARATION_PENDING);
        }
    }

    private int generate(User user, Instant now) {
        var today = now.atZone(SEOUL).toLocalDate();
        Double factor = null;
        int created = 0;
        for (var r : rules.findAllByUser_IdAndEnabledTrueAndDeletedAtIsNullOrderByIdAsc(user.getId())) {
            if (r.getFrequency() == RecurrenceFrequency.WEEKLY && r.getDayOfWeek() != today.getDayOfWeek()) continue;
            Instant start = (r.isAllDay() ? today.atStartOfDay(SEOUL)
                    : today.atTime(r.getScheduledTime()).atZone(SEOUL)).toInstant();
            var period = period(r.getFrequency(), today);
            if (start.isBefore(r.getGenerationNotBefore()) || todos.existsByRecurrenceRuleIdAndRecurrencePeriodStartDate(r.getId(), period)) continue;
            if (factor == null) factor = profiles.fresh(user, now).durationCorrectionFactor();
            int minutes = r.getDefaultUserEstimatedDurationMinutes();
            int baseline = ProfileCalculator.minutes(minutes * factor, 1);
            Instant due = r.isAllDay() ? today.plusDays(1).atStartOfDay(SEOUL).toInstant()
                    : start.plusSeconds(r.getDefaultDeadlineOffsetMinutes() * 60L);
            var todo = Todo.builder().user(user).recurrenceRuleId(r.getId()).recurrenceOccurrenceAt(start)
                    .recurrencePeriodStartDate(period).title(r.getTaskName()).memo(r.getMemo()).allDay(r.isAllDay())
                    .startAt(start).dueAt(due).currentScheduleDate(today).userEstimatedDurationMinutes(minutes)
                    .userEstimateSource(UserEstimateSource.RECURRENCE_TEMPLATE).aiEstimatedDurationMinutes(baseline)
                    .selectedEstimateType(SelectedEstimateType.USER).build();
            todos.saveAndFlush(todo);
            // 새 통계 계산을 레거시로 분류하지 않는다. 외부 호출·성공 Operation은 만들지 않는다.
            drafts.attachGeneratedLocked(user, todo, now);
            created++;
        }
        return created;
    }
}
