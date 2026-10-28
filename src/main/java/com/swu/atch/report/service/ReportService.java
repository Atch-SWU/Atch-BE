package com.swu.atch.report.service;

import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.*;
import com.swu.atch.report.domain.UserDailySettlementState;
import com.swu.atch.report.dto.request.*;
import com.swu.atch.report.dto.response.*;
import com.swu.atch.report.repository.*;
import com.swu.atch.session.domain.*;
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
    private final UserRepository users;
    private final AccountCredentials credentials;
    private final DailySettlementService settlements;
    private final UserDailySettlementStateRepository states;
    private final TodoDailySnapshotRepository snapshots;
    private final TodoRepository todos;
    private final SessionRepository sessions;
    private final TodoSchedulePolicy clock;

    public DailyReportResponse daily(Long id, String stamp, ReportDateQuery query) {
        Instant now = lock(id, stamp); LocalDate date = date(query.date(), now);
        return dailyAt(id, date, now);
    }
    public WeeklyReportResponse weekly(Long id, String stamp, ReportDateQuery query) {
        Instant now = lock(id, stamp); LocalDate date = date(query.date(), now);
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate earliest = clock.seoulDate(now).minusYears(3);
        LocalDate from = monday.isBefore(earliest) ? earliest : monday;
        LocalDate to = monday.plusDays(6).isAfter(clock.seoulDate(now)) ? clock.seoulDate(now) : monday.plusDays(6);
        List<DailyReportResponse> days = from.datesUntil(to.plusDays(1)).map(d -> dailyAt(id,d,now)).toList();
        boolean known = days.stream().allMatch(d -> d.assignedCount() != null);
        Long assigned = known ? days.stream().mapToLong(DailyReportResponse::assignedCount).sum() : null;
        Long completed = known ? days.stream().mapToLong(DailyReportResponse::completedAssignedCount).sum() : null;
        long totalFocus = days.stream().mapToLong(d -> d.closedFocusDurationSeconds()+d.liveFocusDurationSeconds()).sum();
        return new WeeklyReportResponse(monday, from, to, days, assigned, completed,
                assigned == null || assigned == 0 ? null : completed.doubleValue()/assigned,
                days.stream().mapToLong(DailyReportResponse::completedTodoCount).sum(), totalFocus, days.size(),
                (double)totalFocus/days.size(),now);
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
        var completed = todos.findByUser_IdAndDeletedAtIsNullAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDescIdDesc(id,TodoStatus.COMPLETED,from,to);
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
            long seconds=actual.getOrDefault(t.getId(),0L);
            return new TimeComparisonPageResponse.TimeComparisonItem(t.getId(),t.getTitle(),t.getCompletedAt(),t.getUserEstimatedDurationMinutes(),
                    t.getAiEstimatedDurationMinutes(),t.getSelectedEstimateType(),seconds,seconds-t.getUserEstimatedDurationMinutes()*60L,
                    t.getAiEstimatedDurationMinutes()==null?null:seconds-t.getAiEstimatedDurationMinutes()*60L);
        }).toList();
        return new TimeComparisonPageResponse(query.from(),query.to(),items,page.getNumber(),page.getSize(),page.getTotalElements(),page.getTotalPages(),page.hasNext(),now);
    }
    private DailyReportResponse dailyAt(Long id,LocalDate date,Instant now) {
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
        Instant from=start(date),to=start(date.plusDays(1));
        long closed=0,live=0,pauses=0;
        for(var s:sessions.findOverlapping(id,from,to)) {
            long seconds=SessionTimeBuckets.seconds(s,from,to,now);
            if(s.getEndedAt()==null) live+=seconds; else closed+=seconds;
            if(s.getEndReason()==SessionEndReason.PAUSED && !s.getEndedAt().isBefore(from) && s.getEndedAt().isBefore(to) && !s.getEndedAt().isAfter(now)) pauses++;
        }
        long completedCount=todos.findByUser_IdAndDeletedAtIsNullAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtDescIdDesc(id,TodoStatus.COMPLETED,from,to).size();
        return new DailyReportResponse(date,state,assigned,completed,assigned==null||assigned==0?null:completed.doubleValue()/assigned,
                completedCount,overdue,closed,live,pauses,sessions.countResumes(id,from,to.isAfter(now)?now.plusNanos(1_000):to),now);
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
    private Map<Long,Long> actuals(Long id,List<Todo> list) {
        Map<Long,Long> result=new HashMap<>();
        if(!list.isEmpty()) for(var item:sessions.sumClosedDurationSecondsByTodoIdsAndUserId(list.stream().map(Todo::getId).toList(),id))
            result.put(item.getTodoId(),item.getDurationSeconds());
        return result;
    }
    private BusinessException invalid(){return new BusinessException(ErrorCode.INVALID_REQUEST);}
}
