package com.swu.atch.todo.service;

import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.todo.dto.request.TodoListQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TodoSchedulePolicyTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 29);
    private final TodoSchedulePolicy policy = at("2026-10-28T15:00:00Z");

    @Test
    void seoulMidnightChangesTodayAndNewCreationRestrictionTogether() {
        var before = at("2026-10-28T14:59:59.999999Z");
        assertThat(before.seoulDate(before.now())).isEqualTo(DAY.minusDays(1));
        before.requireNewStartDate(DAY.minusDays(1), before.now());
        assertThat(policy.seoulDate(policy.now())).isEqualTo(DAY);
        assertThrows(BusinessException.class, () -> policy.requireNewStartDate(DAY.minusDays(1), policy.now()));
        policy.requireNewStartDate(DAY, policy.now());
    }

    @Test
    void allDayUsesExclusiveNextMidnightAndTimedValuesUseSeoul() {
        var allDay = policy.schedule(DAY, null, DAY, null, true);
        assertThat(allDay.startAt()).isEqualTo(Instant.parse("2026-10-28T15:00:00Z"));
        assertThat(allDay.dueAt()).isEqualTo(Instant.parse("2026-10-29T15:00:00Z"));
        var timed = policy.schedule(DAY, LocalTime.of(9, 0), DAY, LocalTime.of(10, 0), false);
        assertThat(timed.startAt()).isEqualTo(Instant.parse("2026-10-29T00:00:00Z"));
        assertThat(timed.dueAt()).isEqualTo(Instant.parse("2026-10-29T01:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"same-instant", "reverse", "missing-start-time", "missing-due-time", "all-day-different-dates", "all-day-with-time"})
    void rejectsInconsistentSchedule(String kind) {
        LocalDate dueDate = kind.equals("all-day-different-dates") ? DAY.plusDays(1) : DAY;
        boolean allDay = kind.startsWith("all-day");
        LocalTime start = allDay && !kind.equals("all-day-with-time") || kind.equals("missing-start-time") ? null : LocalTime.of(9, 0);
        LocalTime due = allDay || kind.equals("missing-due-time") ? null
                : LocalTime.of(kind.equals("same-instant") ? 9 : kind.equals("reverse") ? 8 : 10, 0);
        assertThrows(BusinessException.class, () -> policy.schedule(DAY, start, dueDate, due, allDay));
    }

    @Test
    void validatesOrderAfterNormalizingDatabaseTimestampPrecision() {
        assertThrows(BusinessException.class, () -> policy.schedule(DAY, LocalTime.of(9, 0, 0, 100),
                DAY, LocalTime.of(9, 0, 0, 900), false));
        var result = policy.schedule(DAY, LocalTime.of(9, 0, 0, 123456789),
                DAY, LocalTime.of(10, 0, 0, 123456789), false);
        assertThat(result.startAt().getNano()).isEqualTo(123456000);
        assertThat(result.dueAt().getNano()).isEqualTo(123456000);
    }

    @Test
    void dateAndInclusiveRangeAndDefaultTodayResolveWithoutChangingDates() {
        var defaults = policy.listRange(query(null, null, null), policy.now());
        assertThat(defaults.from()).isEqualTo(DAY);
        assertThat(defaults.to()).isEqualTo(DAY);
        var specific = policy.listRange(query(DAY.minusDays(10), null, null), policy.now());
        assertThat(specific.from()).isEqualTo(DAY.minusDays(10));
        assertThat(specific.to()).isEqualTo(specific.from());
        assertThat(policy.range(DAY.minusDays(2), DAY.plusDays(2)).to()).isEqualTo(DAY.plusDays(2));
    }

    @Test
    void rejectsMixedMissingAndReversedRange() {
        for (var query : List.of(query(DAY, DAY, DAY), query(null, DAY, null),
                query(null, null, DAY), query(null, DAY, DAY.minusDays(1)))) {
            assertThrows(BusinessException.class, () -> policy.listRange(query, policy.now()));
        }
    }

    private TodoListQuery query(LocalDate date, LocalDate from, LocalDate to) {
        return new TodoListQuery(date, from, to, null, null, null);
    }

    private TodoSchedulePolicy at(String instant) {
        return new TodoSchedulePolicy(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
