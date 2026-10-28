package com.swu.atch.session.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.todo.dto.response.TodoResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 완료된 Todo의 누적 집중 기록과 다음 작업 후보. 기존 완료 API 응답은 유지
@Schema(description = "완료된 Todo의 누적 집중 기록과 다음 작업 후보. 기존 완료 API 응답은 유지")
public record TodoCompletionSummaryResponse(
        @Schema(description = "해당 투두의 상세 정보입니다.")
        TodoResponse todo,
        @Schema(description = "일시정지 횟수입니다.")
        long pauseCount,
        @Schema(description = "일시정지 또는 전환 종료 이후 작업을 재개한 횟수입니다. 최초 시작은 포함하지 않습니다.")
        long resumeCount,
        @Schema(description = "다른 작업으로 전환한 횟수입니다.")
        long switchCount,
        @Schema(description = "완료 시각이 마감보다 늦어진 시간(초). 마감 전에 완료했으면 0입니다.")
        long deadlineOverrunSeconds,
        @Schema(description = "최종 선택 예상시간보다 더 집중한 시간(초). 초과하지 않았으면 0입니다.")
        long estimateOverrunSeconds,
        @Schema(description = "다음에 수행할 수 있는 투두 후보(최대 3개)입니다.")
        List<NextTodo> nextTodos
) {
    public record NextTodo(
            @Schema(description = "서버가 발급한 식별자. 관련 API를 호출할 때 경로 또는 요청 필드에 전달합니다.")
            Long id,
            @Schema(description = "태스크명(투두 이름)입니다.")
            String title,
            @Schema(description = "현재 캘린더상 배정 날짜(서울 기준). 이월되면 최초 시작 날짜와 다를 수 있습니다.")
            LocalDate currentScheduleDate,
            @Schema(description = "최초 마감 일시(UTC). 하루종일 일정이면 다음 날 서울 00:00을 종료 경계로 사용합니다.")
            Instant dueAt,
            @Schema(description = "최종 선택된 예상 작업 소요시간(분). 타이머 등에는 이 값을 사용합니다.")
            int estimatedDurationMinutes) { }
}

