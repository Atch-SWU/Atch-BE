package com.swu.atch.report.service;

import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.*;
import com.swu.atch.report.dto.request.*;
import com.swu.atch.report.dto.response.*;
import com.swu.atch.report.repository.*;
import com.swu.atch.session.repository.SessionRepository;
import com.swu.atch.todo.domain.*;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.todo.service.TodoSchedulePolicy;
import com.swu.atch.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int STREAK_LOOKBACK_DAYS = 28;
    private static final int ACTUAL_DURATION_BATCH_SIZE = 1_000;
    private final UserRepository users;
    private final AccountCredentials credentials;
    private final DailySettlementService settlements;
    private final UserDailySettlementStateRepository states;
    private final TodoDailySnapshotRepository snapshots;
    private final TodoRepository todos;
    private final SessionRepository sessions;
    private final TodoSchedulePolicy clock;

    public DailyReportResponse daily(Long id, String stamp, ReportDateQuery query) {
        Instant now = lock(id, stamp);
        LocalDate date = date(query.date(), now);
        Instant from = start(date);
        Instant to = start(date.plusDays(1));
        var focus = DailyFocusSummary.summarize(sessions.findOverlapping(id, from, to), date.atStartOfDay(SEOUL), now, true);
        return dailyAt(id, date, now, completedTodos(id, from, to).size(), focus);
    }

    public WeeklyReportResponse weekly(Long id, String stamp, ReportDateQuery query) {
        Instant now = lock(id, stamp);
        LocalDate date = date(query.date(), now);
        LocalDate today = clock.seoulDate(now);
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate earliest = today.minusYears(3);
        LocalDate from = monday.isBefore(earliest) ? earliest : monday;
        LocalDate weekEnd = monday.plusDays(6);
        LocalDate to = weekEnd.isAfter(today) ? today : weekEnd;
        Instant rangeStart = start(from);
        Instant rangeEnd = start(to.plusDays(1));
        var recordedSessions = sessions.findOverlapping(id, rangeStart, rangeEnd);
        var completedTodos = completedTodos(id, rangeStart, rangeEnd);
        Map<LocalDate, Long> completedCounts = new HashMap<>();
        for (Todo todo : completedTodos) {
            completedCounts.merge(clock.seoulDate(todo.getCompletedAt()), 1L, Long::sum);
        }
        List<DailyReportResponse> days = from.datesUntil(to.plusDays(1))
                .map(day -> dailyAt(id, day, now, completedCounts.getOrDefault(day, 0L),
                        DailyFocusSummary.summarize(recordedSessions, day.atStartOfDay(SEOUL), now, false)))
                .toList();

        boolean known = days.stream().allMatch(day -> day.assignedCount() != null);
        Long assigned = known ? days.stream().mapToLong(DailyReportResponse::assignedCount).sum() : null;
        Long completed = known ? days.stream().mapToLong(DailyReportResponse::completedAssignedCount).sum() : null;
        long totalFocus = 0;
        long peakSeconds = 0;
        LocalDate peakDate = null;
        for (var day : days) {
            long seconds = focusSeconds(day);
            totalFocus += seconds;
            if (seconds > peakSeconds) {
                peakDate = day.date();
                peakSeconds = seconds;
            }
        }

        return new WeeklyReportResponse(monday, from, to, days, assigned, completed,
                assigned == null || assigned == 0 ? null : completed.doubleValue() / assigned,
                (long) completedTodos.size(), totalFocus, days.size(), (double) totalFocus / days.size(), now,
                peakDate, focusStreakDays(id, days, today, earliest, now), aiPrediction(id, completedTodos));
    }

    private int focusStreakDays(Long id, List<DailyReportResponse> days, LocalDate today,
                                LocalDate earliest, Instant now) {
        int index = days.size() - 1;
        var lastDay = days.get(index);
        if (lastDay.date().equals(today) && focusSeconds(lastDay) == 0) index--;
        int streak = 0;
        for (; index >= 0; index--) {
            if (focusSeconds(days.get(index)) == 0) return streak;
            streak++;
        }

        LocalDate end = days.getFirst().date().minusDays(1);
        while (!end.isBefore(earliest)) {
            LocalDate from = end.minusDays(STREAK_LOOKBACK_DAYS - 1);
            if (from.isBefore(earliest)) from = earliest;
            var recordedSessions = sessions.findOverlapping(id, start(from), start(end.plusDays(1)));
            for (LocalDate day = end; !day.isBefore(from); day = day.minusDays(1)) {
                Instant dayStart = start(day);
                Instant dayEnd = start(day.plusDays(1));
                boolean focused = recordedSessions.stream()
                        .anyMatch(session -> SessionTimeBuckets.seconds(session, dayStart, dayEnd, now) > 0);
                if (!focused) return streak;
                streak++;
            }
            end = from.minusDays(1);
        }
        return streak;
    }

    private long focusSeconds(DailyReportResponse day) {
        return day.closedFocusDurationSeconds() + day.liveFocusDurationSeconds();
    }

    private WeeklyReportResponse.AiPredictionSummary aiPrediction(Long id, List<Todo> completedTodos) {
        var withEstimate = completedTodos.stream()
                .filter(todo -> todo.getAiEstimatedDurationMinutes() != null && todo.getAiEstimatedDurationMinutes() > 0)
                .toList();
        var measuredSeconds = actuals(id, withEstimate);
        long count = 0;
        double closeness = 0;
        double signedError = 0;
        double absoluteError = 0;
        for (Todo todo : withEstimate) {
            Long actual = measuredSeconds.get(todo.getId());
            if (actual == null) continue;
            long estimate = todo.getAiEstimatedDurationMinutes() * 60L;
            long error = actual - estimate;
            count++;
            closeness += (double) Math.min(actual, estimate) / Math.max(actual, estimate);
            signedError += error;
            absoluteError += Math.abs(error);
        }
        if (count == 0) return new WeeklyReportResponse.AiPredictionSummary(0, null, null, null);
        return new WeeklyReportResponse.AiPredictionSummary(count, closeness / count, absoluteError / count, signedError / count);
    }

    public DailyTodoHistoryResponse history(Long id, String stamp, ReportDateQuery query) {
        Instant now = lock(id, stamp); LocalDate date = date(query.date(), now); var state = coverage(id,date,now);
        List<DailyTodoHistoryResponse.SnapshotItem> items;
        if (date.equals(clock.seoulDate(now)) && state == DailyReportResponse.State.LIVE) {
            items = todos.findAllByUser_IdAndCurrentScheduleDateAndDeletedAtIsNullOrderByStartAtAscIdAsc(id,date).stream()
                    .map(t -> new DailyTodoHistoryResponse.SnapshotItem(t.getId(),t.getTitle(),t.getStatus(),t.getStartAt(),t.getDueAt(),
                            t.getCompletedAt(),date.isAfter(clock.seoulDate(t.getStartAt())),overdue(t,now),now)).toList();
        } else items = snapshots.findByUser_IdAndScheduleDateOrderByIdAsc(id,date).stream()
                .map(s -> new DailyTodoHistoryResponse.SnapshotItem(s.getTodo().getId(),s.getTodo().getTitle(),s.getStatusAtDayEnd(),
                        s.getStartAtSnapshot(),s.getDueAtSnapshot(),s.getCompletedAtSnapshot(),s.isWasRolledOver(),s.isWasOverdue(),s.getSnapshotAt())).toList();
        return new DailyTodoHistoryResponse(date,state,items,now);
    }
    public FocusPatternResponse patterns(Long id, String stamp, ReportRangeQuery query) {
        Instant now = lock(id, stamp); range(query.from(),query.to(),now);
        Instant from = start(query.from()), to = start(query.to().plusDays(1));
        long[] closed = new long[24], live = new long[24];
        for (var s : sessions.findOverlapping(id,from,to)) {
            Instant point = s.getStartedAt().isAfter(from) ? s.getStartedAt() : from;
            Instant end = s.getEndedAt() == null ? now : s.getEndedAt();
            if (end.isAfter(to)) end=to;
            if (end.isAfter(now)) end=now;
            while (point.isBefore(end)) {
                var zoned = point.atZone(SEOUL); Instant nextHour = zoned.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant();
                Instant right = nextHour.isBefore(end) ? nextHour : end;
                long seconds = SessionTimeBuckets.seconds(s,point,right,now);
                (s.getEndedAt() == null ? live : closed)[zoned.getHour()] += seconds;
                point=right;
            }
        }
        var completed = completedTodos(id, from, to);
        Map<Long,Long> actual = actuals(id,completed);
        List<FocusPatternResponse.WeekdayBucket> weekdays = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            var group=completed.stream().filter(t -> t.getCompletedAt().atZone(SEOUL).getDayOfWeek()==day).toList();
            long seconds=group.stream().mapToLong(t -> actual.getOrDefault(t.getId(),0L)).sum();
            weekdays.add(new FocusPatternResponse.WeekdayBucket(day,group.size(),seconds,group.isEmpty()?null:(double)seconds/group.size()));
        }
        List<FocusPatternResponse.HourlyBucket> hours=new ArrayList<>();
        for(int hour=0;hour<24;hour++) hours.add(new FocusPatternResponse.HourlyBucket(hour,closed[hour],live[hour]));
        return new FocusPatternResponse(query.from(),query.to(),hours,weekdays,now);
    }
    public TimeComparisonPageResponse comparisons(Long id,String stamp,TimeComparisonQuery query) {
        Instant now=lock(id,stamp); range(query.from(),query.to(),now);
        if(query.page()<0||query.size()<1||query.size()>100) throw invalid();
        var page=todos.findByUser_IdAndDeletedAtIsNullAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(id,TodoStatus.COMPLETED,start(query.from()),start(query.to().plusDays(1)),
                PageRequest.of(query.page(),query.size(),Sort.by(Sort.Direction.DESC,"completedAt","id")));
        var actual=actuals(id,page.getContent());
        var items=page.getContent().stream().map(t -> {
            // 집계 행이 없으면 타이머로 실제 0초를 기록한 경우와 구분한다.
            Long measured = actual.get(t.getId());
            return new TimeComparisonPageResponse.TimeComparisonItem(t.getId(),t.getTitle(),t.getCompletedAt(),t.getUserEstimatedDurationMinutes(),
                    t.getAiEstimatedDurationMinutes(),t.getSelectedEstimateType(),measured == null ? 0L : measured,
                    comparisonError(measured, t.getUserEstimatedDurationMinutes()),
                    comparisonError(measured, t.getAiEstimatedDurationMinutes()));
        }).toList();
        return new TimeComparisonPageResponse(query.from(),query.to(),items,page.getNumber(),page.getSize(),page.getTotalElements(),page.getTotalPages(),page.hasNext(),now);
    }
    private Long comparisonError(Long measured, Integer estimatedMinutes) {
        return measured == null || estimatedMinutes == null ? null : measured - estimatedMinutes * 60L;
    }
    private DailyReportResponse dailyAt(Long id, LocalDate date, Instant now, long completedCount, DailyFocusSummary focus) {
        var state=coverage(id,date,now);
        Long assigned=null, completed=null, overdue=null;
        if(state==DailyReportResponse.State.LIVE) {
            var list=todos.findAllByUser_IdAndCurrentScheduleDateAndDeletedAtIsNullOrderByStartAtAscIdAsc(id,date);
            assigned=(long)list.size(); completed=list.stream().filter(t->t.getStatus()==TodoStatus.COMPLETED).count();
            overdue=list.stream().filter(t->overdue(t,now)).count();
        } else if(state==DailyReportResponse.State.FINALIZED) {
            var list=snapshots.findByUser_IdAndScheduleDateOrderByIdAsc(id,date);
            assigned=(long)list.size(); completed=list.stream().filter(s->s.getStatusAtDayEnd()==TodoStatus.COMPLETED).count();
            overdue=list.stream().filter(s->s.isWasOverdue()).count();
        }
        Instant from = start(date);
        Instant to = start(date.plusDays(1));
        long resumes = sessions.countResumes(id, from, to.isAfter(now) ? now.plusNanos(1_000) : to);
        return new DailyReportResponse(date, state, assigned, completed,
                assigned == null || assigned == 0 ? null : completed.doubleValue() / assigned,
                completedCount, overdue, focus.closedSeconds(), focus.liveSeconds(), focus.pauses(), resumes, now,
                focus.buckets(), focus.details());
    }
    private DailyReportResponse.State coverage(Long id,LocalDate date,Instant now) {
        var state=states.findById(id).orElse(null);
        if(state==null||date.isBefore(state.getTrackingStartedOn())) return DailyReportResponse.State.UNAVAILABLE;
        if(date.isBefore(state.getNextSettlementDate())) return DailyReportResponse.State.FINALIZED;
        if(date.equals(clock.seoulDate(now))&&!state.getNextSettlementDate().isBefore(date)) return DailyReportResponse.State.LIVE;
        return DailyReportResponse.State.PENDING_FINALIZATION;
    }
    private Instant lock(Long id,String stamp) {
        boolean prepared=settlements.prepare(id,stamp);
        var user=users.findByIdForUpdate(id).orElseThrow(()->new BusinessException(ErrorCode.UNAUTHORIZED));
        credentials.requireCurrent(credentials.localAccount(id),stamp);
        return prepared ? settlements.requireCurrentLocked(user) : clock.now();
    }
    private LocalDate date(LocalDate value,Instant now) {
        LocalDate result=value==null?clock.seoulDate(now):value;range(result,result,now);return result;
    }
    private void range(LocalDate from,LocalDate to,Instant now) {
        LocalDate today=clock.seoulDate(now);
        if(from==null||to==null||from.isAfter(to)||from.isBefore(today.minusYears(3))||to.isAfter(today)) throw invalid();
    }
    private Instant start(LocalDate date) { return date.atStartOfDay(SEOUL).toInstant(); }
    private boolean overdue(Todo t,Instant asOf) {
        return t.getStatus()==TodoStatus.COMPLETED ? t.getCompletedAt()!=null&&t.getCompletedAt().isAfter(t.getDueAt()) : !asOf.isBefore(t.getDueAt());
    }
    private List<Todo> completedTodos(Long id, Instant from, Instant to) {
        return todos.findByUser_IdAndDeletedAtIsNullAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDescIdDesc(
                id, TodoStatus.COMPLETED, from, to);
    }

    private Map<Long, Long> actuals(Long id, List<Todo> list) {
        Map<Long, Long> result = new HashMap<>();
        for (int offset = 0; offset < list.size(); offset += ACTUAL_DURATION_BATCH_SIZE) {
            int end = Math.min(offset + ACTUAL_DURATION_BATCH_SIZE, list.size());
            var ids = list.subList(offset, end).stream().map(Todo::getId).toList();
            for (var item : sessions.sumClosedDurationSecondsByTodoIdsAndUserId(ids, id)) {
                result.put(item.getTodoId(), item.getDurationSeconds());
            }
        }
        return result;
    }
    private BusinessException invalid(){return new BusinessException(ErrorCode.INVALID_REQUEST);}
}
