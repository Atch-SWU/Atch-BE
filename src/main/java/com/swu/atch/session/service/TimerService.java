package com.swu.atch.session.service;

import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.*;
import com.swu.atch.report.service.DailySettlementService;
import com.swu.atch.session.domain.*;
import com.swu.atch.session.dto.request.*;
import com.swu.atch.session.dto.response.*;
import com.swu.atch.session.repository.*;
import com.swu.atch.setting.domain.UserSetting;
import com.swu.atch.setting.repository.UserSettingRepository;
import com.swu.atch.todo.domain.*;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.todo.service.TodoSchedulePolicy;
import com.swu.atch.todo.service.TodoService;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class TimerService {
    private final UserRepository users;
    private final TodoRepository todos;
    private final SessionRepository sessions;
    private final TimerSwitchRepository switches;
    private final UserSettingRepository settings;
    private final AccountCredentials credentials;
    private final TodoSchedulePolicy clock;
    private final DailySettlementService settlements;
    private final TodoService todoService;

    public TimerStateResponse start(Long id, String stamp, Long todoId, TimerStartRequest request) {
        var locked = lock(id, stamp); var todo = active(id, todoId); Instant now = locked.now();
        requireNoWait(id, now); requireNoOpen(id);
        var last = latestTodo(id, todoId);
        if (!Objects.equals(request.expectedLastSessionId(), last == null ? null : last.getId())) throw conflict();
        if (todo.getStatus() != TodoStatus.PENDING) throw conflict();
        requireSameFocusOrUnrestricted(id, todoId);
        startSession(todo, now); return state(id, now);
    }
    public TimerStateResponse pause(Long id, String stamp, Long todoId, TimerPauseRequest request) {
        var locked = lock(id, stamp); var todo = active(id, todoId); Instant now = locked.now();
        requireNoWait(id, now);
        var open = onlyOpen(id);
        if (open == null || !open.getId().equals(request.sessionId()) || !open.getTodo().getId().equals(todoId)
                || todo.getStatus() != TodoStatus.IN_PROGRESS) throw conflict();
        finish(open, now, SessionEndReason.PAUSED); todo.pause(); sessions.flush(); return state(id, now);
    }
    public TimerStateResponse resume(Long id, String stamp, Long todoId, TimerResumeRequest request) {
        var locked = lock(id, stamp); var todo = active(id, todoId); Instant now = locked.now();
        requireNoWait(id, now); requireNoOpen(id);
        var last = latestTodo(id, todoId);
        if (todo.getStatus() != TodoStatus.PAUSED || last == null || last.getEndedAt() == null
                || !last.getId().equals(request.lastSessionId())
                || last.getEndReason() != SessionEndReason.PAUSED && last.getEndReason() != SessionEndReason.SWITCHED) throw conflict();
        requireSameFocusOrUnrestricted(id, todoId);
        startSession(todo, now); return state(id, now);
    }
    public TimerStateResponse current(Long id, String stamp) {
        var locked = lock(id, stamp); return state(id, locked.now());
    }
    public TimerSwitchResponse requestSwitch(Long id, String stamp, TimerSwitchRequest request) {
        var locked = lock(id, stamp); var user = locked.user(); Instant now = locked.now();
        var source = active(id, request.sourceTodoId()); var target = active(id, request.targetTodoId());
        if (source.getId().equals(target.getId())) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var receipt = switches.findFirstByUser_IdAndSourceSession_IdAndTargetTodo_IdAndStateOrderByIdDesc(
                id, request.sourceSessionId(), target.getId(), TimerSwitchState.STARTED);
        if (receipt.isPresent() && receipt.get().getSourceTodo().getId().equals(source.getId())) return response(receipt.get(), id, now);
        var waiting = pending(id, now);
        if (waiting != null) {
            if (waiting.getSourceTodo().getId().equals(source.getId()) && waiting.getTargetTodo().getId().equals(target.getId())
                    && waiting.getSourceSession().getId().equals(request.sourceSessionId())) return response(waiting, id, now);
            throw conflict();
        }
        var focus = latestFocus(id);
        if (focus == null || !focus.getTodo().getId().equals(source.getId()) || !focus.getId().equals(request.sourceSessionId())
                || !startable(target)) throw conflict();
        var open = onlyOpen(id);
        if (open != null) {
            if (!open.getId().equals(focus.getId()) || source.getStatus() != TodoStatus.IN_PROGRESS) throw conflict();
            finish(open, now, SessionEndReason.SWITCHED); source.pause();
        } else if (source.getStatus() != TodoStatus.PAUSED) throw conflict();
        var setting = setting(user);
        Instant ready = setting.isTaskSwitchRestrictionEnabled() ? now.plusSeconds(setting.getTaskSwitchWaitMinutes() * 60L) : now;
        // 만료된 WAITING의 상태 변경을 먼저 반영하여 부분 UNIQUE를 해제한다.
        switches.flush();
        var item = switches.saveAndFlush(TimerSwitch.request(user, source, target, focus, now, ready, ready.plusSeconds(600)));
        if (!setting.isTaskSwitchRestrictionEnabled()) item.start(startSession(target, now));
        switches.flush(); return response(item, id, now);
    }
    public TimerSwitchResponse confirmSwitch(Long id, String stamp, UUID requestId) {
        var locked = lock(id, stamp); Instant now = locked.now();
        var item = switches.findByRequestIdAndUser_Id(requestId, id).orElseThrow(this::notFound);
        if (item.getState() == TimerSwitchState.STARTED) return response(item, id, now);
        item.expire(now);
        if (item.getState() != TimerSwitchState.WAITING || now.isBefore(item.getReadyAt())) throw conflict();
        var source = active(id, item.getSourceTodo().getId()); var target = active(id, item.getTargetTodo().getId());
        var focus = latestFocus(id);
        if (focus == null || !focus.getId().equals(item.getSourceSession().getId()) || source.getStatus() != TodoStatus.PAUSED
                || !startable(target)) throw conflict();
        requireNoOpen(id);
        item.start(startSession(target, now)); switches.flush(); return response(item, id, now);
    }
    public void cancelSwitch(Long id, String stamp, UUID requestId) {
        var locked = lock(id, stamp);
        var item = switches.findByRequestIdAndUser_Id(requestId, id).orElseThrow(this::notFound);
        item.expire(locked.now());
        if (item.getState() == TimerSwitchState.STARTED) throw conflict();
        item.cancel(); switches.flush();
    }
    public SessionPageResponse history(Long id, String stamp, Long todoId, SessionPageQuery query) {
        lock(id, stamp); active(id, todoId);
        var page = sessions.findByTodo_IdAndTodo_User_Id(todoId, id,
                PageRequest.of(query.page(), query.size(), Sort.by("startedAt", "id")));
        return new SessionPageResponse(todoId, page.map(s -> new SessionResponse(s.getId(), todoId, s.getStartedAt(),
                s.getEndedAt(), s.getDurationSeconds(), s.getEndReason())).getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
    public TodoCompletionSummaryResponse summary(Long id, String stamp, Long todoId) {
        var locked = lock(id, stamp); var todo = active(id, todoId);
        if (todo.getStatus() != TodoStatus.COMPLETED || todo.getCompletedAt() == null) throw conflict();
        var history = sessions.findByTodo_IdAndTodo_User_IdOrderByStartedAtAscIdAsc(todoId, id);
        long pauses = 0, resumes = 0, changed = 0;
        Session previous = null;
        for (var s : history) {
            if (s.getEndReason() == SessionEndReason.PAUSED) pauses++;
            if (s.getEndReason() == SessionEndReason.SWITCHED) changed++;
            boolean counted = s.getResumeCounted() != null ? s.getResumeCounted()
                    : previous != null && (previous.getEndReason() == SessionEndReason.PAUSED || previous.getEndReason() == SessionEndReason.SWITCHED);
            if (counted) resumes++;
            previous = s;
        }
        var next = todos.findTop3ByUser_IdAndDeletedAtIsNullAndStatusInOrderByCurrentScheduleDateAscStartAtAscIdAsc(id, List.of(TodoStatus.PENDING, TodoStatus.PAUSED))
                .stream().map(t -> new TodoCompletionSummaryResponse.NextTodo(t.getId(), t.getTitle(), t.getCurrentScheduleDate(), t.getDueAt(), estimate(t))).toList();
        long actual = sessions.sumDurationSecondsByTodoIdAndUserId(todoId, id);
        return new TodoCompletionSummaryResponse(todoService.toResponse(todo, actual, locked.now()), pauses, resumes, changed,
                Math.max(0, Duration.between(todo.getDueAt(), todo.getCompletedAt()).getSeconds()), Math.max(0, actual - estimate(todo) * 60L), next);
    }

    private LockedUser lock(Long id, String stamp) {
        settlements.requirePrepared(id, stamp);
        var user = users.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        credentials.requireCurrent(credentials.localAccount(id), stamp);
        return new LockedUser(user, settlements.requireCurrentLocked(user));
    }
    private Todo active(Long id, Long todoId) {
        var todo = todos.findOwnedByIdForUpdate(todoId, id).orElseThrow(this::notFound);
        if (todo.getDeletedAt() != null) throw notFound(); return todo;
    }
    private Session latestTodo(Long id, Long todoId) {
        return sessions.findFirstByTodo_IdAndTodo_User_IdOrderByStartedAtDescIdDesc(todoId, id).orElse(null);
    }
    private Session latestFocus(Long id) {
        var last = sessions.findFirstByTodo_User_IdOrderByStartedAtDescIdDesc(id).orElse(null);
        if (last == null || last.getEndReason() == SessionEndReason.COMPLETED || last.getTodo().getDeletedAt() != null || last.getTodo().getStatus() == TodoStatus.COMPLETED || last.getTodo().getStatus() == TodoStatus.PENDING) return null;
        return last;
    }
    private Session onlyOpen(Long id) {
        var all = sessions.findByTodo_User_IdAndEndedAtIsNull(id);
        if (all.size() > 1) throw conflict(); return all.isEmpty() ? null : all.getFirst();
    }
    private void requireNoOpen(Long id) { if (onlyOpen(id) != null) throw conflict(); }
    private void requireSameFocusOrUnrestricted(Long id, Long todoId) {
        var focus = latestFocus(id);
        // 대기 비활성 상태여도 기존 열린 구간은 별도로 금지한다.
        if (focus != null && !focus.getTodo().getId().equals(todoId)
                && settings.findById(id).map(UserSetting::isTaskSwitchRestrictionEnabled).orElse(true))
            throw new BusinessException(ErrorCode.TIMER_SWITCH_WAIT_REQUIRED);
    }
    private TimerSwitch pending(Long id, Instant now) {
        var item = switches.findByUser_IdAndState(id, TimerSwitchState.WAITING).orElse(null);
        if (item != null) {
            item.expire(now);
            if (item.getState() != TimerSwitchState.WAITING) { switches.flush(); return null; }
            var focus = latestFocus(id);
            if (focus == null || !focus.getId().equals(item.getSourceSession().getId())
                    || item.getTargetTodo().getDeletedAt() != null || item.getTargetTodo().getStatus() == TodoStatus.COMPLETED) {
                item.cancel(); switches.flush(); return null;
            }
        }
        return item;
    }
    private void requireNoWait(Long id, Instant now) { if (pending(id, now) != null) throw conflict(); }
    private Session startSession(Todo todo, Instant now) {
        boolean resume = todo.getStatus() == TodoStatus.PAUSED;
        if (resume) {
            var last = latestTodo(todo.getUser().getId(), todo.getId());
            if (last == null || last.getEndedAt() == null || last.getEndReason() == SessionEndReason.COMPLETED) throw conflict();
        }
        todo.start(); return sessions.saveAndFlush(resume ? Session.resume(todo, now) : Session.start(todo, now));
    }
    private void finish(Session session, Instant now, SessionEndReason reason) {
        long seconds = Duration.between(session.getStartedAt(), now).getSeconds();
        if (now.isBefore(session.getStartedAt()) || seconds > Integer.MAX_VALUE) throw conflict();
        session.finish(now, reason);
    }
    private UserSetting setting(User user) {
        return settings.findById(user.getId()).orElseGet(() -> settings.saveAndFlush(new UserSetting(user)));
    }
    private TimerStateResponse state(Long id, Instant now) {
        var waiting = pending(id, now); var open = onlyOpen(id); var focus = latestFocus(id);
        if (open != null && (focus == null || !focus.getId().equals(open.getId()) || open.getTodo().getStatus() != TodoStatus.IN_PROGRESS)) throw conflict();
        if (focus == null) return new TimerStateResponse(TimerStateResponse.State.IDLE, null, null, null, null, null, null, 0,0,0,0,0,null,now);
        var todo = focus.getTodo();
        if (open == null && todo.getStatus() == TodoStatus.IN_PROGRESS || open != null && now.isBefore(open.getStartedAt())) throw conflict();
        long closed = sessions.sumDurationSecondsByTodoIdAndUserId(todo.getId(), id);
        long elapsed = open == null ? 0 : Math.max(0, Duration.between(open.getStartedAt(), now).getSeconds());
        long total = closed + elapsed; long estimated = estimate(todo) * 60L;
        var wait = waiting == null ? null : new TimerStateResponse.SwitchWait(waiting.getRequestId(), waiting.getTargetTodo().getId(),
                waiting.getRequestedAt(), waiting.getReadyAt(), waiting.getExpiresAt());
        return new TimerStateResponse(wait != null ? TimerStateResponse.State.SWITCH_WAITING : open != null ? TimerStateResponse.State.RUNNING : TimerStateResponse.State.PAUSED,
                todo.getId(), todo.getTitle(), todo.getStatus(), open == null ? null : open.getId(), focus.getId(),
                open == null ? null : open.getStartedAt(), closed, elapsed, total, Math.max(0,estimated-total), Math.max(0,total-estimated),wait,now);
    }
    private TimerSwitchResponse response(TimerSwitch item, Long id, Instant now) {
        return new TimerSwitchResponse(item.getRequestId(), item.getState() == TimerSwitchState.STARTED ? TimerSwitchResponse.State.STARTED : TimerSwitchResponse.State.WAITING,
                item.getSourceTodo().getId(), item.getTargetTodo().getId(), item.getRequestedAt(), item.getReadyAt(), item.getExpiresAt(), state(id,now));
    }
    private record LockedUser(User user, Instant now) { }
    private boolean startable(Todo todo) { return todo.getStatus() == TodoStatus.PENDING || todo.getStatus() == TodoStatus.PAUSED; }
    private int estimate(Todo t) { return t.getSelectedEstimateType() == SelectedEstimateType.AI ? t.getAiEstimatedDurationMinutes() : t.getUserEstimatedDurationMinutes(); }
    private BusinessException conflict() { return new BusinessException(ErrorCode.TIMER_STATE_CONFLICT); }
    private BusinessException notFound() { return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); }
}
