package com.swu.atch.report.service;

import com.swu.atch.report.dto.response.DailyReportResponse.FocusBucket;
import com.swu.atch.report.dto.response.DailyReportResponse.FocusDetails;
import com.swu.atch.report.dto.response.DailyReportResponse.FocusTodo;
import com.swu.atch.session.domain.Session;
import com.swu.atch.session.domain.SessionEndReason;
import com.swu.atch.todo.domain.Todo;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

record DailyFocusSummary(
        List<FocusBucket> buckets,
        FocusDetails details,
        long closedSeconds,
        long liveSeconds,
        long pauses
) {
    private static final int HOURS_PER_BUCKET = 2;
    private static final int BUCKET_COUNT = 24 / HOURS_PER_BUCKET;

    static DailyFocusSummary summarize(List<Session> sessions, ZonedDateTime dayStart,
                                      Instant asOf, boolean includeDetails) {
        Instant[] boundaries = new Instant[BUCKET_COUNT + 1];
        for (int index = 0; index <= BUCKET_COUNT; index++) {
            boundaries[index] = dayStart.plusHours((long) index * HOURS_PER_BUCKET).toInstant();
        }
        Instant from = boundaries[0];
        Instant to = boundaries[BUCKET_COUNT];
        long[] closedBuckets = new long[BUCKET_COUNT];
        long[] liveBuckets = new long[BUCKET_COUNT];
        long pauses = 0;
        Map<Long, TodoFocus> todos = new HashMap<>();
        for (Session session : sessions) {
            Instant endedAt = session.getEndedAt();
            boolean running = endedAt == null;
            boolean paused = !running && session.getEndReason() == SessionEndReason.PAUSED
                    && !endedAt.isBefore(from) && endedAt.isBefore(to) && !endedAt.isAfter(asOf);
            if (paused) pauses++;

            long seconds = SessionTimeBuckets.seconds(session, from, to, asOf);
            if (seconds > 0) {
                long[] buckets = running ? liveBuckets : closedBuckets;
                for (int index = 0; index < BUCKET_COUNT; index++) {
                    buckets[index] += SessionTimeBuckets.seconds(session, boundaries[index], boundaries[index + 1], asOf);
                }
            }
            if (includeDetails && (seconds > 0 || paused)) {
                Todo todo = session.getTodo();
                Instant activityAt = session.getStartedAt().isBefore(from) ? from : session.getStartedAt();
                // 일시정지 시각만 조회 범위에 있는 구간은 해당 이벤트를 목록 시각으로 표시한다.
                if (seconds == 0) activityAt = endedAt;
                todos.computeIfAbsent(todo.getId(), ignored -> new TodoFocus(todo))
                        .add(activityAt, seconds, running, paused);
            }
        }

        long closed = 0;
        long live = 0;
        long peakSeconds = 0;
        Integer peakHour = null;
        List<FocusBucket> buckets = new ArrayList<>(BUCKET_COUNT);
        for (int index = 0; index < BUCKET_COUNT; index++) {
            int hour = index * HOURS_PER_BUCKET;
            closed += closedBuckets[index];
            live += liveBuckets[index];
            buckets.add(new FocusBucket(hour, closedBuckets[index], liveBuckets[index]));
            long bucketSeconds = closedBuckets[index] + liveBuckets[index];
            if (bucketSeconds > peakSeconds) {
                peakHour = hour;
                peakSeconds = bucketSeconds;
            }
        }
        FocusDetails details = includeDetails ? details(todos, closed + live, peakHour, from, to) : null;
        return new DailyFocusSummary(List.copyOf(buckets), details, closed, live, pauses);
    }

    private static FocusDetails details(Map<Long, TodoFocus> todos, long totalSeconds, Integer peakHour,
                                        Instant from, Instant to) {
        long focusedTodoCount = todos.values().stream().filter(todo -> todo.closed > 0 || todo.live > 0).count();
        Double average = focusedTodoCount == 0 ? null : (double) totalSeconds / focusedTodoCount;
        var rows = todos.values().stream().filter(todo -> todo.createdDuring(from, to)).map(TodoFocus::response)
                .sorted(Comparator.comparing(FocusTodo::firstActivityAt).thenComparing(FocusTodo::todoId))
                .toList();
        return new FocusDetails(peakHour, average, rows);
    }

    private static final class TodoFocus {
        private final Todo todo;
        private Instant firstActivityAt;
        private long closed;
        private long live;
        private long pauses;

        private TodoFocus(Todo todo) {
            this.todo = todo;
        }

        private void add(Instant activityAt, long seconds, boolean running, boolean paused) {
            if (firstActivityAt == null || activityAt.isBefore(firstActivityAt)) firstActivityAt = activityAt;
            if (running) live += seconds;
            else closed += seconds;
            if (paused) pauses++;
        }

        private boolean createdDuring(Instant from, Instant to) {
            Instant createdAt = todo.getCreatedAt();
            return !createdAt.isBefore(from) && createdAt.isBefore(to);
        }

        private FocusTodo response() {
            String title = todo.getDeletedAt() == null ? todo.getTitle() : null;
            return new FocusTodo(todo.getId(), title, firstActivityAt, closed, live, pauses);
        }
    }
}
