package com.swu.atch.todo.service;

import com.swu.atch.todo.domain.*;
import com.swu.atch.todo.dto.response.TodoResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class TodoResponseMapper {
    private final TodoSchedulePolicy schedule;

    public TodoResponse toResponse(Todo todo, long actualSeconds, Instant now) {
        boolean completed = todo.getStatus() == TodoStatus.COMPLETED;
        Integer estimate =
                todo.getSelectedEstimateType() == SelectedEstimateType.AI
                        ? todo.getAiEstimatedDurationMinutes()
                        : todo.getUserEstimatedDurationMinutes();
        return new TodoResponse(
                todo.getId(),
                todo.getTitle(),
                todo.getSourceTaskName(),
                todo.getMemo(),
                todo.getStartAt(),
                todo.getDueAt(),
                todo.getCurrentScheduleDate(),
                todo.isAllDay(),
                todo.getStatus(),
                todo.getCompletedAt(),
                todo.getUserEstimatedDurationMinutes(),
                todo.getUserEstimateSource(),
                todo.getAiEstimatedDurationMinutes(),
                todo.getSelectedEstimateType(),
                estimate,
                actualSeconds,
                !completed && !now.isBefore(todo.getDueAt()),
                todo.getCurrentScheduleDate().isAfter(schedule.seoulDate(todo.getStartAt())),
                todo.getRecurrenceRuleId(),
                todo.getRecurrenceOccurrenceAt(),
                todo.getRecurrencePeriodStartDate(),
                todo.getCreatedAt(),
                todo.getUpdatedAt());
    }
}
