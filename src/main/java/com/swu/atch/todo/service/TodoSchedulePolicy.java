package com.swu.atch.todo.service;

import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.todo.dto.request.TodoListQuery;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

// 서울 현지 입력과 저장 시각을 구분한다. 시계는 테스트에서만 고정 값으로 교체한다.
@Component
public class TodoSchedulePolicy {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock;

    public TodoSchedulePolicy() { this(Clock.systemUTC()); }

    public TodoSchedulePolicy(Clock clock) { this.clock = Objects.requireNonNull(clock); }

    public Instant now() {
        // PostgreSQL TIMESTAMPTZ 정밀도와 맞춰 저장·재조회 시각의 경계 차이를 없앤다.
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    public LocalDate seoulDate(Instant instant) { return instant.atZone(SEOUL).toLocalDate(); }

    public Schedule schedule(LocalDate startDate, LocalTime startTime, LocalDate dueDate,
                             LocalTime dueTime, Boolean allDay) {
        if (startDate == null || dueDate == null || allDay == null) throw invalid();
        try {
            Instant start;
            Instant due;
            if (allDay) {
                if (!startDate.equals(dueDate) || startTime != null || dueTime != null) throw invalid();
                start = startDate.atStartOfDay(SEOUL).toInstant();
                due = startDate.plusDays(1).atStartOfDay(SEOUL).toInstant();
            } else {
                if (startTime == null || dueTime == null) throw invalid();
                start = startDate.atTime(startTime).atZone(SEOUL).toInstant();
                due = dueDate.atTime(dueTime).atZone(SEOUL).toInstant();
            }
            // 저장 정밀도보다 짧은 구간이 DB에서 같은 시각으로 바뀌어 500이 되지 않도록 한다.
            start = start.truncatedTo(ChronoUnit.MICROS);
            due = due.truncatedTo(ChronoUnit.MICROS);
            if (!due.isAfter(start)) throw invalid();
            return new Schedule(start, due);
        } catch (DateTimeException ex) {
            throw invalid();
        }
    }

    public void requireNewStartDate(LocalDate startDate, Instant now) {
        if (startDate == null || startDate.isBefore(seoulDate(now))) throw invalid();
    }

    public DateRange listRange(TodoListQuery query, Instant now) {
        if (query.page() < 0 || query.size() < 1 || query.size() > 100
                || query.status().stream().anyMatch(Objects::isNull)) throw invalid();
        if (query.date() != null) {
            if (query.from() != null || query.to() != null) throw invalid();
            return new DateRange(query.date(), query.date());
        }
        if (query.from() == null && query.to() == null) {
            LocalDate today = seoulDate(now);
            return new DateRange(today, today);
        }
        return range(query.from(), query.to());
    }

    public DateRange range(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) throw invalid();
        return new DateRange(from, to);
    }

    private BusinessException invalid() { return new BusinessException(ErrorCode.INVALID_REQUEST); }

    public record Schedule(Instant startAt, Instant dueAt) { }
    public record DateRange(LocalDate from, LocalDate to) { }
}
