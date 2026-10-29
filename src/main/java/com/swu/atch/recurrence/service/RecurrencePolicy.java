package com.swu.atch.recurrence.service;

import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.recurrence.domain.*;
import java.time.*;
import java.util.Objects;

// 서울 날짜·논리 회차·시각/하루종일 검증을 한 곳에서 계산한다.
public final class RecurrencePolicy {
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private RecurrencePolicy() { }

    public record Key(String name, RecurrenceFrequency frequency, DayOfWeek day, boolean allDay, LocalTime time) {
        public boolean overlaps(Key other) {
            return name.equals(other.name) && allDay == other.allDay && Objects.equals(time, other.time)
                    && (frequency == RecurrenceFrequency.DAILY || other.frequency == RecurrenceFrequency.DAILY
                    || day == other.day);
        }
    }
    public static Key key(RepeatPattern p) {
        return new Key(p.getTaskName(), p.getFrequency(), p.getDayOfWeek(), p.isAllDay(), p.getScheduledTime());
    }
    public static Key key(RecurrenceRule r) {
        return new Key(r.getTaskName(), r.getFrequency(), r.getDayOfWeek(), r.isAllDay(), r.getScheduledTime());
    }
    public static int offset(boolean allDay, Integer value) {
        if (allDay) {
            if (value != null && value != 1440) throw invalid();
            return 1440;
        }
        if (value == null || value <= 0) throw invalid();
        return value;
    }
    public static void schedule(RecurrenceFrequency frequency, DayOfWeek day, boolean allDay, LocalTime time) {
        if (frequency == RecurrenceFrequency.DAILY && day != null || frequency == RecurrenceFrequency.WEEKLY && day == null
                || (allDay ? time != null : time == null || time.getNano() % 1000 != 0)) throw invalid();
    }
    public static LocalDate period(RecurrenceFrequency frequency, LocalDate date) {
        return frequency == RecurrenceFrequency.DAILY ? date : date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }
    public static Instant firstGeneration(Instant now) {
        return now.atZone(SEOUL).toLocalDate().plusDays(1).atStartOfDay(SEOUL).toInstant();
    }
    public static void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalid();
    }
    public static void version(Instant expected, Instant actual) {
        if (expected == null || !expected.equals(actual)) throw conflict();
    }
    public static BusinessException invalid() { return new BusinessException(ErrorCode.INVALID_REQUEST); }
    public static BusinessException conflict() { return new BusinessException(ErrorCode.RECURRENCE_STATE_CONFLICT); }
    public static BusinessException missing() { return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); }
}
