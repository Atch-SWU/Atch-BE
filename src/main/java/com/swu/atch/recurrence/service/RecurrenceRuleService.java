package com.swu.atch.recurrence.service;

import com.swu.atch.common.exception.*;
import com.swu.atch.recurrence.domain.*;
import com.swu.atch.recurrence.dto.request.*;
import com.swu.atch.recurrence.dto.response.*;
import com.swu.atch.recurrence.repository.*;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.todo.service.TodoSchedulePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.swu.atch.recurrence.service.RecurrencePolicy.*;

@Service
@RequiredArgsConstructor
@Transactional
public class RecurrenceRuleService {
    private final RecurrenceAccess access;
    private final RecurrenceRecommendationService recommendations;
    private final RepeatPatternRepository patterns;
    private final RecurrenceRuleRepository rules;
    private final TodoRepository todos;
    private final TodoSchedulePolicy clock;

    public record Registered(RecurrenceRuleResponse rule, boolean replayed) { }
    public Registered register(Long id, String stamp, Long patternId, RecurrenceRuleRegisterRequest input) {
        var user = access.lock(id, stamp); access.requireRecommendation(user);
        var p = recommendations.owned(id, patternId);
        int deadline = offset(p.isAllDay(), input.defaultDeadlineOffsetMinutes());
        if (input.defaultUserEstimatedDurationMinutes() == null || input.defaultUserEstimatedDurationMinutes() <= 0
                || input.memo() != null && input.memo().length() > 2000) throw invalid();
        var old = rules.findBySourcePattern_IdAndUser_Id(patternId, id);
        if (old.isPresent()) {
            var r = old.get();
            if (r.getDeletedAt() == null && r.isEnabled() && key(r).equals(key(p)) && Objects.equals(r.getMemo(), input.memo())
                    && r.getDefaultDeadlineOffsetMinutes() == deadline
                    && r.getDefaultUserEstimatedDurationMinutes().equals(input.defaultUserEstimatedDurationMinutes()))
                return new Registered(response(r), true);
            throw conflict();
        }
        version(input.expectedUpdatedAt(), p.getUpdatedAt());
        var now = clock.now();
        recommendations.refreshLocked(user, now);
        version(input.expectedUpdatedAt(), p.getUpdatedAt());
        if (p.getStatus() != RepeatPatternStatus.SUGGESTED) throw conflict();
        requireNoOverlap(id, null, key(p));
        var rule = RecurrenceRule.builder().user(user).sourcePattern(p).taskName(p.getTaskName()).memo(input.memo())
                .dayOfWeek(p.getDayOfWeek()).scheduledTime(p.getScheduledTime()).defaultDeadlineOffsetMinutes(deadline)
                .defaultUserEstimatedDurationMinutes(input.defaultUserEstimatedDurationMinutes())
                .generationNotBefore(firstGeneration(now)).build();
        p.register();
        rules.saveAndFlush(rule);
        return new Registered(response(rule), false);
    }

    public RecurrenceRulePageResponse list(Long id, String stamp, RecurrenceRuleListQuery query) {
        access.lock(id, stamp); page(query.page(), query.size());
        var paging = org.springframework.data.domain.PageRequest.of(query.page(), query.size(),
                org.springframework.data.domain.Sort.by("id").descending());
        var result = query.enabled() == null ? rules.findAllByUser_IdAndDeletedAtIsNull(id, paging)
                : rules.findAllByUser_IdAndDeletedAtIsNullAndEnabled(id, query.enabled(), paging);
        return new RecurrenceRulePageResponse(result.getContent().stream().map(this::response).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }
    public RecurrenceRuleResponse detail(Long id, String stamp, Long ruleId) {
        access.lock(id, stamp); return response(active(id, ruleId));
    }
    public RecurrenceRuleResponse update(Long id, String stamp, Long ruleId, RecurrenceRuleUpdateRequest input) {
        access.lock(id, stamp); var r = active(id, ruleId);
        if (input.taskName() == null || input.taskName().isBlank() || input.taskName().trim().length() > 200
                || input.allDay() == null || input.defaultUserEstimatedDurationMinutes() == null
                || input.defaultUserEstimatedDurationMinutes() <= 0 || input.memo() != null && input.memo().length() > 2000) throw invalid();
        int deadline = offset(input.allDay(), input.defaultDeadlineOffsetMinutes());
        schedule(r.getFrequency(), input.dayOfWeek(), input.allDay(), input.scheduledTime());
        var k = new Key(input.taskName().trim(), r.getFrequency(), input.dayOfWeek(), input.allDay(), input.scheduledTime());
        if (key(r).equals(k) && Objects.equals(r.getMemo(), input.memo()) && r.getDefaultDeadlineOffsetMinutes() == deadline
                && r.getDefaultUserEstimatedDurationMinutes().equals(input.defaultUserEstimatedDurationMinutes())) return response(r);
        version(input.expectedUpdatedAt(), r.getUpdatedAt());
        requireNoOverlap(id, ruleId, k);
        r.updateDetails(k.name(), input.memo(), k.day(), k.time(), k.allDay(), deadline,
                input.defaultUserEstimatedDurationMinutes(), clock.now());
        rules.flush(); return response(r);
    }
    public RecurrenceRuleResponse enable(Long id, String stamp, Long ruleId, RecurrenceRuleEnabledRequest input) {
        access.lock(id, stamp); var r = active(id, ruleId);
        if (input.enabled() == null) throw invalid();
        if (r.isEnabled() == input.enabled()) return response(r);
        version(input.expectedUpdatedAt(), r.getUpdatedAt());
        r.changeEnabled(input.enabled(), clock.now());
        rules.flush(); return response(r);
    }
    public void delete(Long id, String stamp, Long ruleId) {
        access.lock(id, stamp);
        var r = rules.findByIdAndUser_Id(ruleId, id).orElseThrow(RecurrencePolicy::missing);
        if (r.getDeletedAt() != null) return;
        r.getSourcePattern().advanceDetectionBoundary(todos.maxIdForUser(id));
        r.softDelete(clock.now());
        rules.flush();
    }
    private RecurrenceRule active(Long user, Long id) {
        return rules.findByIdAndUser_IdAndDeletedAtIsNull(id, user).orElseThrow(RecurrencePolicy::missing);
    }
    private void requireNoOverlap(Long user, Long except, Key candidate) {
        if (rules.findAllByUser_IdAndDeletedAtIsNullOrderByIdDesc(user).stream()
                .anyMatch(r -> !Objects.equals(r.getId(), except) && key(r).overlaps(candidate)))
            throw new BusinessException(ErrorCode.RECURRENCE_RULE_OVERLAP);
    }
    private RecurrenceRuleResponse response(RecurrenceRule r) {
        return new RecurrenceRuleResponse(r.getId(), r.getSourcePattern().getId(), r.getTaskName(), r.getFrequency(),
                r.getDayOfWeek(), r.isAllDay(), r.getScheduledTime(), r.getMemo(), r.getDefaultDeadlineOffsetMinutes(),
                r.getDefaultUserEstimatedDurationMinutes(), r.isEnabled(), r.getGenerationNotBefore(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
