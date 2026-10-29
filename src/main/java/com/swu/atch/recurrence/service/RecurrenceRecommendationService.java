package com.swu.atch.recurrence.service;

import com.swu.atch.common.exception.*;
import com.swu.atch.recurrence.domain.*;
import com.swu.atch.recurrence.dto.request.*;
import com.swu.atch.recurrence.dto.response.*;
import com.swu.atch.recurrence.repository.*;
import com.swu.atch.todo.domain.Todo;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.todo.service.TodoSchedulePolicy;
import com.swu.atch.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.swu.atch.recurrence.service.RecurrencePolicy.*;

@Service
@RequiredArgsConstructor
@Transactional
public class RecurrenceRecommendationService {
    private final RecurrenceAccess access;
    private final RepeatPatternRepository patterns;
    private final RecurrenceRuleRepository rules;
    private final TodoRepository todos;
    private final TodoSchedulePolicy clock;

    public RecurrenceRecommendationPageResponse list(Long id, String stamp, RecurrenceRecommendationQuery query) {
        page(query.page(), query.size());
        var user = access.lock(id, stamp);
        var now = clock.now();
        if (!access.settings(user).isRepeatRecommendationEnabled())
            return new RecurrenceRecommendationPageResponse(false, List.of(), query.page(), query.size(), 0, 0, false, now);
        var sources = refreshLocked(user, now);
        var result = patterns.findAllByUser_IdAndStatusOrderByLastSuggestedAtDescIdDesc(id,
                RepeatPatternStatus.SUGGESTED, org.springframework.data.domain.PageRequest.of(query.page(), query.size()));
        var items = result.getContent().stream().map(p -> response(p, sources.get(key(p)))).toList();
        return new RecurrenceRecommendationPageResponse(true, items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.hasNext(), now);
    }

    // 호출자는 User를 잠근다. 원래 startAt으로 재계산하며 GET 횟수를 세지 않는다.
    public Map<Key, Todo> refreshLocked(User user, Instant now) {
        var history = todos.findRecurrenceObservations(user.getId(), now);
        var existing = patterns.findAllByUser_IdOrderByIdAsc(user.getId());
        var registered = rules.findAllByUser_IdOrderByIdAsc(user.getId());
        Map<Key, List<TodoRepository.RecurrenceObservation>> groups = new LinkedHashMap<>();
        for (var t : history) {
            var date = t.getStartAt().atZone(SEOUL).toLocalDate();
            var time = t.isAllDay() ? null : t.getStartAt().atZone(SEOUL).toLocalTime();
            groups.computeIfAbsent(new Key(t.getTitle().trim(), RecurrenceFrequency.DAILY, null, t.isAllDay(), time), k -> new ArrayList<>()).add(t);
            groups.computeIfAbsent(new Key(t.getTitle().trim(), RecurrenceFrequency.WEEKLY, date.getDayOfWeek(), t.isAllDay(), time), k -> new ArrayList<>()).add(t);
        }
        Map<Key, RepeatPattern> open = new HashMap<>();
        for (var p : existing) if (p.getStatus() == RepeatPatternStatus.DETECTING || p.getStatus() == RepeatPatternStatus.SUGGESTED) {
            if (open.put(key(p), p) != null) throw conflict();
            groups.computeIfAbsent(key(p), k -> new ArrayList<>());
        }
        Map<Key, Integer> counts = new HashMap<>();
        Map<Key, TodoRepository.RecurrenceObservation> sources = new HashMap<>();
        for (var entry : groups.entrySet()) {
            var k = entry.getKey(); var p = open.get(k);
            long boundary = p == null ? 0 : p.getDetectionAfterTodoId();
            for (var r : registered) if (r.getDeletedAt() != null && (key(r).equals(k) || key(r.getSourcePattern()).equals(k)))
                boundary = Math.max(boundary, r.getSourcePattern().getDetectionAfterTodoId());
            final long cutoff = boundary;
            var dates = new TreeMap<LocalDate, TodoRepository.RecurrenceObservation>();
            for (var t : entry.getValue()) if (t.getId() > cutoff) {
                var d = t.getStartAt().atZone(SEOUL).toLocalDate();
                dates.merge(d, t, (a, b) -> a.getId() > b.getId() ? a : b);
            }
            int count = 0; LocalDate previous = null;
            for (var d : dates.descendingKeySet()) {
                if (previous != null && !d.plusDays(k.frequency() == RecurrenceFrequency.DAILY ? 1 : 7).equals(previous)) break;
                count++; previous = d;
            }
            counts.put(k, count);
            if (!dates.isEmpty()) sources.put(k, dates.lastEntry().getValue());
            if (p == null && count > 0 && !blocked(k, existing, registered)) {
                p = RepeatPattern.builder().user(user).taskName(k.name()).frequency(k.frequency()).dayOfWeek(k.day())
                        .allDay(k.allDay()).scheduledTime(k.time()).detectionAfterTodoId(boundary)
                        .occurrenceCount(count).lastOccurrenceDate(dates.lastKey()).build();
                patterns.save(p); open.put(k, p);
            }
        }
        for (var entry : open.entrySet()) {
            var k = entry.getKey(); var p = entry.getValue(); int count = counts.getOrDefault(k, 0);
            var daily = new Key(k.name(), RecurrenceFrequency.DAILY, null, k.allDay(), k.time());
            boolean dailyPreferred = k.frequency() == RecurrenceFrequency.WEEKLY && counts.getOrDefault(daily, 0) >= 2
                    && !blocked(daily, existing, registered);
            boolean suggested = count >= 2 && !dailyPreferred && !blocked(k, existing, registered);
            var source = sources.get(k);
            p.observe(count, source == null ? p.getLastOccurrenceDate() : source.getStartAt().atZone(SEOUL).toLocalDate(), suggested, now);
        }
        patterns.flush();
        // 등록 폼의 메모·기본값은 최신 근거 ID만 일괄 조회한다.
        Map<Long, Todo> sourceTodos = new HashMap<>();
        var ids = sources.values().stream().map(TodoRepository.RecurrenceObservation::getId).distinct().toList();
        todos.findAllById(ids).forEach(t -> sourceTodos.put(t.getId(), t));
        Map<Key, Todo> result = new HashMap<>();
        sources.forEach((k, observation) -> result.put(k, sourceTodos.get(observation.getId())));
        return result;
    }

    private boolean blocked(Key k, List<RepeatPattern> existing, List<RecurrenceRule> registered) {
        return existing.stream().anyMatch(p -> p.getStatus() == RepeatPatternStatus.DISMISSED && key(p).equals(k))
                || registered.stream().anyMatch(r -> r.getDeletedAt() == null
                && (key(r).overlaps(k) || key(r.getSourcePattern()).overlaps(k)));
    }

    public RecommendationActionResponse act(Long id, String stamp, Long patternId, RecommendationActionRequest request, boolean dismiss) {
        var user = access.lock(id, stamp); access.requireRecommendation(user);
        var p = owned(id, patternId);
        // 동일 선택의 재전송은 새 관측 경계를 전진시키지 않는다.
        if (dismiss && p.getStatus() == RepeatPatternStatus.DISMISSED
                || !dismiss && p.getStatus() == RepeatPatternStatus.DETECTING && p.getOccurrenceCount() == 0)
            return action(p);
        version(request.expectedUpdatedAt(), p.getUpdatedAt());
        refreshLocked(user, clock.now());
        if (p.getStatus() != RepeatPatternStatus.SUGGESTED) throw conflict();
        version(request.expectedUpdatedAt(), p.getUpdatedAt());
        if (dismiss) p.dismiss(); else p.skip(todos.maxIdForUser(id));
        patterns.flush();
        return action(p);
    }
    public RepeatPattern owned(Long user, Long id) {
        return patterns.findByIdAndUser_Id(id, user).orElseThrow(RecurrencePolicy::missing);
    }
    private RecommendationActionResponse action(RepeatPattern p) {
        return new RecommendationActionResponse(p.getId(), p.getStatus(), p.getOccurrenceCount(), p.getUpdatedAt());
    }
    private RecurrenceRecommendationResponse response(RepeatPattern p, Todo t) {
        if (t == null) throw conflict();
        long seconds = Duration.between(t.getStartAt(), t.getDueAt()).getSeconds();
        long rounded = seconds / 60 + (seconds % 60 > 0 || t.getDueAt().getNano() != t.getStartAt().getNano() ? 1 : 0);
        Integer offset = p.isAllDay() ? 1440 : rounded > Integer.MAX_VALUE ? null : (int) Math.max(1, rounded);
        var template = new RecurrenceTemplateSuggestion(t.getId(), t.getMemo(), offset, t.getUserEstimatedDurationMinutes());
        return new RecurrenceRecommendationResponse(p.getId(), p.getTaskName(), p.getFrequency(), p.getDayOfWeek(), p.isAllDay(),
                p.getScheduledTime(), p.getOccurrenceCount(), p.getLastOccurrenceDate(), p.getLastSuggestedAt(), p.getUpdatedAt(), template);
    }
}
