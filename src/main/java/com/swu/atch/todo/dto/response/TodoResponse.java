package com.swu.atch.todo.dto.response;

import com.swu.atch.todo.domain.SelectedEstimateType;
import com.swu.atch.todo.domain.TodoStatus;
import com.swu.atch.todo.domain.UserEstimateSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

// 현재 Todo 조회 모델. 과거 날짜 종료 상태를 나타내는 Snapshot 응답과 구분한다.
@Schema(description = "본인 소유의 삭제되지 않은 Todo. 시각은 UTC Instant, 날짜 기준은 Asia/Seoul")
public record TodoResponse(
        @Schema(description = "Todo ID", example = "1") Long id,
        @Schema(description = "작업명", example = "TODO API 설계") String title,
        @Schema(description = "AI 분해 원본 작업명. 일반 Todo는 null") String sourceTaskName,
        @Schema(description = "메모. 없으면 null") String memo,
        @Schema(description = "예정 시작 시각(UTC)", example = "2026-10-29T00:00:00Z") Instant startAt,
        @Schema(description = "마감 시각(UTC). 하루종일이면 다음 서울 날짜 00:00, 미포함", example = "2026-10-29T01:00:00Z") Instant dueAt,
        @Schema(description = "현재 목록·캘린더에 배정된 날짜. 이월 시 원래 시작 날짜와 다를 수 있음", example = "2026-10-29") LocalDate currentScheduleDate,
        @Schema(description = "하루종일 여부", example = "false") boolean allDay,
        @Schema(description = "현재 진행 상태") TodoStatus status,
        @Schema(description = "완료 시각. 미완료 또는 완료 취소 시 null") Instant completedAt,
        @Schema(description = "사용자 예상시간(분)", example = "30") Integer userEstimatedDurationMinutes,
        @Schema(description = "사용자 예상시간의 출처") UserEstimateSource userEstimateSource,
        @Schema(description = "AI 예상시간(분). 제안이 없거나 입력 변경으로 무효화되면 null") Integer aiEstimatedDurationMinutes,
        @Schema(description = "최종 예상시간 선택") SelectedEstimateType selectedEstimateType,
        @Schema(description = "선택값에 따른 실제 적용 예상시간(분)", example = "30") Integer estimatedDurationMinutes,
        @Schema(description = "종료된 Session의 실제 초 합계. 열린 구간 제외, 기록 없으면 0", example = "0") long actualDurationSeconds,
        @Schema(description = "현재 미완료 Todo의 마감 초과 여부. 이월 여부와 독립") boolean overdue,
        @Schema(description = "현재 배정 날짜가 서울 기준 원래 시작 날짜보다 뒤인지 여부") boolean rolledOver,
        @Schema(description = "반복 규칙 ID. 일반 Todo는 null", accessMode = Schema.AccessMode.READ_ONLY) Long recurrenceRuleId,
        @Schema(description = "불변 반복 회차 예정 시각. 일반 Todo는 null", accessMode = Schema.AccessMode.READ_ONLY) Instant recurrenceOccurrenceAt,
        @Schema(description = "불변 논리 회차 시작 날짜. 일반 Todo는 null", accessMode = Schema.AccessMode.READ_ONLY) LocalDate recurrencePeriodStartDate,
        @Schema(description = "생성 시각") Instant createdAt,
        @Schema(description = "최종 수정 시각") Instant updatedAt
) {
}
