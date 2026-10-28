package com.swu.atch.todo.service;

import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.session.domain.Session;
import com.swu.atch.session.domain.SessionEndReason;
import com.swu.atch.session.repository.SessionRepository;
import com.swu.atch.todo.domain.SelectedEstimateType;
import com.swu.atch.todo.domain.Todo;
import com.swu.atch.todo.domain.TodoStatus;
import com.swu.atch.todo.domain.UserEstimateSource;
import com.swu.atch.todo.dto.request.*;
import com.swu.atch.todo.dto.response.*;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class TodoService {
    private static final Sort LIST_ORDER = Sort.by("currentScheduleDate", "startAt", "id");
    private final TodoResponseMapper responseMapper;
    private final com.swu.atch.ai.service.AiTodoService aiTodos;
    private final TodoRepository todos;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final AccountCredentials credentials;
    private final TodoSchedulePolicy schedule;
    private final com.swu.atch.report.service.DailySettlementService settlements;
    private final com.swu.atch.session.repository.TimerSwitchRepository switches;

    @Transactional
    public TodoResponse create(Long userId, String stamp, TodoCreateRequest request) {
        if (request.estimateChoice() != null) return aiTodos.create(userId, stamp, request).todo();
        if (request.creationRequestId() != null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var locked = lockUser(userId, stamp);
        User user = locked.user();
        Instant now = locked.now();
        schedule.requireNewStartDate(request.startDate(), now);
        var times = schedule.schedule(request.startDate(), request.startTime(), request.dueDate(), request.dueTime(), request.allDay());
        var todo = Todo.builder().user(user).title(request.title()).memo(request.memo())
                .startAt(times.startAt()).dueAt(times.dueAt()).currentScheduleDate(request.startDate()).allDay(request.allDay())
                .userEstimatedDurationMinutes(request.userEstimatedDurationMinutes())
                .userEstimateSource(UserEstimateSource.USER_INPUT).selectedEstimateType(SelectedEstimateType.USER).build();
        // ID·Timestamp를 확정해 저장된 값을 응답한다.
        todos.saveAndFlush(todo);
        return toResponse(todo, 0L, now);
    }

    public TodoPageResponse list(Long userId, TodoListQuery query) {
        Instant now = lockForRead(userId);
        var range = schedule.listRange(query, now);
        var pageable = PageRequest.of(query.page(), query.size(), LIST_ORDER);
        var page = query.status().isEmpty()
                ? todos.findAllByUser_IdAndCurrentScheduleDateBetweenAndDeletedAtIsNull(userId, range.from(), range.to(), pageable)
                : todos.findAllByUser_IdAndCurrentScheduleDateBetweenAndStatusInAndDeletedAtIsNull(userId, range.from(), range.to(), query.status(), pageable);
        List<Long> ids = page.getContent().stream().map(Todo::getId).toList();
        Map<Long, Long> durations = new HashMap<>();
        if (!ids.isEmpty()) {
            for (var total : sessions.sumClosedDurationSecondsByTodoIdsAndUserId(ids, userId)) {
                durations.put(total.getTodoId(), total.getDurationSeconds());
            }
        }
        var items = page.getContent().stream().map(todo -> toResponse(todo, durations.getOrDefault(todo.getId(), 0L), now)).toList();
        return new TodoPageResponse(range.from(), range.to(), items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    public TodoCalendarResponse calendar(Long userId, TodoDateRangeQuery query) {
        lockForRead(userId);
        var range = schedule.range(query.from(), query.to());
        return new TodoCalendarResponse(range.from(), range.to(), todos.findActiveScheduleDates(userId, range.from(), range.to()));
    }

    public TodoResponse detail(Long userId, Long todoId) {
        Instant now = lockForRead(userId);
        var todo = todos.findByIdAndUser_IdAndDeletedAtIsNull(todoId, userId).orElseThrow(this::notFound);
        return toResponse(todo, actualSeconds(todo, userId), now);
    }

    @Transactional
    public TodoResponse update(Long userId, String stamp, Long todoId, TodoUpdateRequest request) {
        var locked = lockUser(userId, stamp);
        var todo = lockActiveTodo(userId, todoId);
        requirePaused(todo, userId);
        if (todo.getStatus() == TodoStatus.COMPLETED) throw new BusinessException(ErrorCode.TODO_REOPEN_REQUIRED);
        var times = schedule.schedule(request.startDate(), request.startTime(), request.dueDate(), request.dueTime(), request.allDay());
        var selection = aiTodos.resolveExisting(userId, stamp, todo, request);
        todo.updateDetails(request.title(), request.memo(), times.startAt(), times.dueAt(), request.allDay(), request.userEstimatedDurationMinutes());
        if (selection != null) todo.applyEstimateChoice(selection.aiMinutes(), selection.type());
        movePastAssignmentToToday(todo, locked.now());
        todos.flush();
        return toResponse(todo, actualSeconds(todo, userId), locked.now());
    }

    @Transactional
    public void delete(Long userId, String stamp, Long todoId) {
        var locked = lockUser(userId, stamp);
        var todo = todos.findOwnedByIdForUpdate(todoId, userId).orElseThrow(this::notFound);
        if (todo.getDeletedAt() != null) return;
        requirePaused(todo, userId);
        todo.softDelete(locked.now());
        cancelWaiting(userId, todoId);
        todos.flush();
    }

    @Transactional
    public TodoResponse complete(Long userId, String stamp, Long todoId) {
        var locked = lockUser(userId, stamp);
        var todo = lockActiveTodo(userId, todoId);
        Optional<Session> open = openSession(todo, userId);
        Instant now = locked.now();
        if (todo.getStatus() == TodoStatus.COMPLETED) {
            if (open.isPresent()) throw conflict();
            return toResponse(todo, actualSeconds(todo, userId), now);
        }
        open.ifPresent(session -> {
            // 잘못된 시작 시각을 보정하거나 Session을 지우지 않고 전체 변경을 롤백한다.
            if (now.isBefore(session.getStartedAt())
                    || Duration.between(session.getStartedAt(), now).getSeconds() > Integer.MAX_VALUE) throw conflict();
            session.finish(now, SessionEndReason.COMPLETED);
        });
        todo.complete(now);
        cancelWaiting(userId, todoId);
        // Session 종료와 Todo 상태를 함께 반영한 뒤 실제 초를 집계한다.
        todos.flush();
        return toResponse(todo, actualSeconds(todo, userId), now);
    }

    @Transactional
    public TodoResponse reopen(Long userId, String stamp, Long todoId) {
        var locked = lockUser(userId, stamp);
        var todo = lockActiveTodo(userId, todoId);
        if (todo.getStatus() == TodoStatus.COMPLETED && openSession(todo, userId).isPresent()) throw conflict();
        todo.reopen();
        movePastAssignmentToToday(todo, locked.now());
        todos.flush();
        return toResponse(todo, actualSeconds(todo, userId), locked.now());
    }

    private LockedUser lockUser(Long userId, String stamp) {
        settlements.requirePrepared(userId, stamp);
        // 기존 계정 변경·탈퇴와 같은 잠금 순서로 쓰기를 직렬화하고 오래된 인증을 재확인한다.
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        credentials.requireCurrent(credentials.localAccount(userId), stamp);
        return new LockedUser(user, settlements.requireCurrentLocked(user));
    }

    private Instant lockForRead(Long userId) {
        settlements.requirePrepared(userId, null);
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return settlements.requireCurrentLocked(user);
    }

    private void movePastAssignmentToToday(Todo todo, Instant now) {
        var today = schedule.seoulDate(now);
        if (todo.getStatus() != TodoStatus.COMPLETED && todo.getCurrentScheduleDate().isBefore(today)) todo.rollOverTo(today);
    }

    private void cancelWaiting(Long userId, Long todoId) {
        switches.findByUser_IdAndState(userId, com.swu.atch.session.domain.TimerSwitchState.WAITING).ifPresent(item -> {
            if (item.getSourceTodo().getId().equals(todoId) || item.getTargetTodo().getId().equals(todoId)) item.cancel();
        });
    }

    private Todo lockActiveTodo(Long userId, Long todoId) {
        var todo = todos.findOwnedByIdForUpdate(todoId, userId).orElseThrow(this::notFound);
        if (todo.getDeletedAt() != null) throw notFound();
        return todo;
    }

    private void requirePaused(Todo todo, Long userId) {
        if (todo.getStatus() == TodoStatus.IN_PROGRESS || openSession(todo, userId).isPresent()) {
            throw new BusinessException(ErrorCode.TODO_PAUSE_REQUIRED);
        }
    }

    private Optional<Session> openSession(Todo todo, Long userId) {
        return sessions.findByTodo_IdAndTodo_User_IdAndEndedAtIsNull(todo.getId(), userId);
    }

    private long actualSeconds(Todo todo, Long userId) {
        return sessions.sumDurationSecondsByTodoIdAndUserId(todo.getId(), userId);
    }

    // 이미 잠근 Todo를 응답으로 변환한다. 새로운 정산·조회·시각 갱신은 하지 않는다.
    public TodoResponse toResponse(Todo todo, long actualSeconds, Instant now) {
        return responseMapper.toResponse(todo, actualSeconds, now);
    }

    private record LockedUser(User user, Instant now) { }

    private BusinessException notFound() { return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); }
    private BusinessException conflict() { return new BusinessException(ErrorCode.RESOURCE_CONFLICT); }
}
