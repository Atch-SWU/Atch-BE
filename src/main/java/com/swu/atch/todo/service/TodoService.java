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
@Transactional(readOnly = true)
public class TodoService {
    private static final Sort LIST_ORDER = Sort.by("currentScheduleDate", "startAt", "id");
    private final TodoRepository todos;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final AccountCredentials credentials;
    private final TodoSchedulePolicy schedule;

    @Transactional
    public TodoResponse create(Long userId, String stamp, TodoCreateRequest request) {
        User user = lockUser(userId, stamp);
        Instant now = schedule.now();
        schedule.requireNewStartDate(request.startDate(), now);
        var times = schedule.schedule(request.startDate(), request.startTime(), request.dueDate(), request.dueTime(), request.allDay());
        var todo = Todo.builder().user(user).title(request.title()).memo(request.memo())
                .startAt(times.startAt()).dueAt(times.dueAt()).currentScheduleDate(request.startDate()).allDay(request.allDay())
                .userEstimatedDurationMinutes(request.userEstimatedDurationMinutes())
                .userEstimateSource(UserEstimateSource.USER_INPUT).selectedEstimateType(SelectedEstimateType.USER).build();
        // ID·Timestamp를 확정해 저장된 값을 응답한다.
        todos.saveAndFlush(todo);
        return response(todo, 0L, now);
    }

    public TodoPageResponse list(Long userId, TodoListQuery query) {
        Instant now = schedule.now();
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
        var items = page.getContent().stream().map(todo -> response(todo, durations.getOrDefault(todo.getId(), 0L), now)).toList();
        return new TodoPageResponse(range.from(), range.to(), items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    public TodoCalendarResponse calendar(Long userId, TodoDateRangeQuery query) {
        var range = schedule.range(query.from(), query.to());
        return new TodoCalendarResponse(range.from(), range.to(), todos.findActiveScheduleDates(userId, range.from(), range.to()));
    }

    public TodoResponse detail(Long userId, Long todoId) {
        var todo = todos.findByIdAndUser_IdAndDeletedAtIsNull(todoId, userId).orElseThrow(this::notFound);
        return response(todo, actualSeconds(todo, userId), schedule.now());
    }

    @Transactional
    public TodoResponse update(Long userId, String stamp, Long todoId, TodoUpdateRequest request) {
        lockUser(userId, stamp);
        var todo = lockActiveTodo(userId, todoId);
        requirePaused(todo, userId);
        if (todo.getStatus() == TodoStatus.COMPLETED) throw new BusinessException(ErrorCode.TODO_REOPEN_REQUIRED);
        var times = schedule.schedule(request.startDate(), request.startTime(), request.dueDate(), request.dueTime(), request.allDay());
        todo.updateDetails(request.title(), request.memo(), times.startAt(), times.dueAt(), request.allDay(), request.userEstimatedDurationMinutes());
        todos.flush();
        return response(todo, actualSeconds(todo, userId), schedule.now());
    }

    @Transactional
    public void delete(Long userId, String stamp, Long todoId) {
        lockUser(userId, stamp);
        var todo = todos.findOwnedByIdForUpdate(todoId, userId).orElseThrow(this::notFound);
        if (todo.getDeletedAt() != null) return;
        requirePaused(todo, userId);
        todo.softDelete(schedule.now());
        todos.flush();
    }

    @Transactional
    public TodoResponse complete(Long userId, String stamp, Long todoId) {
        lockUser(userId, stamp);
        var todo = lockActiveTodo(userId, todoId);
        Optional<Session> open = openSession(todo, userId);
        Instant now = schedule.now();
        if (todo.getStatus() == TodoStatus.COMPLETED) {
            if (open.isPresent()) throw conflict();
            return response(todo, actualSeconds(todo, userId), now);
        }
        open.ifPresent(session -> {
            // 잘못된 시작 시각을 보정하거나 Session을 지우지 않고 전체 변경을 롤백한다.
            if (now.isBefore(session.getStartedAt())
                    || Duration.between(session.getStartedAt(), now).getSeconds() > Integer.MAX_VALUE) throw conflict();
            session.finish(now, SessionEndReason.COMPLETED);
        });
        todo.complete(now);
        // Session 종료와 Todo 상태를 함께 반영한 뒤 실제 초를 집계한다.
        todos.flush();
        return response(todo, actualSeconds(todo, userId), now);
    }

    @Transactional
    public TodoResponse reopen(Long userId, String stamp, Long todoId) {
        lockUser(userId, stamp);
        var todo = lockActiveTodo(userId, todoId);
        if (todo.getStatus() == TodoStatus.COMPLETED && openSession(todo, userId).isPresent()) throw conflict();
        todo.reopen();
        todos.flush();
        return response(todo, actualSeconds(todo, userId), schedule.now());
    }

    private User lockUser(Long userId, String stamp) {
        // 기존 계정 변경·탈퇴와 같은 잠금 순서로 쓰기를 직렬화하고 오래된 인증을 재확인한다.
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        credentials.requireCurrent(credentials.localAccount(userId), stamp);
        return user;
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

    private TodoResponse response(Todo todo, long actualSeconds, Instant now) {
        boolean completed = todo.getStatus() == TodoStatus.COMPLETED;
        Integer estimate = todo.getSelectedEstimateType() == SelectedEstimateType.AI
                ? todo.getAiEstimatedDurationMinutes() : todo.getUserEstimatedDurationMinutes();
        return new TodoResponse(todo.getId(), todo.getTitle(), todo.getSourceTaskName(), todo.getMemo(),
                todo.getStartAt(), todo.getDueAt(), todo.getCurrentScheduleDate(), todo.isAllDay(), todo.getStatus(), todo.getCompletedAt(),
                todo.getUserEstimatedDurationMinutes(), todo.getUserEstimateSource(), todo.getAiEstimatedDurationMinutes(),
                todo.getSelectedEstimateType(), estimate, actualSeconds, !completed && !now.isBefore(todo.getDueAt()),
                todo.getCurrentScheduleDate().isAfter(schedule.seoulDate(todo.getStartAt())),
                todo.getRecurrenceRuleId(), todo.getRecurrenceOccurrenceAt(), todo.getRecurrencePeriodStartDate(),
                todo.getCreatedAt(), todo.getUpdatedAt());
    }

    private BusinessException notFound() { return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); }
    private BusinessException conflict() { return new BusinessException(ErrorCode.RESOURCE_CONFLICT); }
}
