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
        @Schema(description = "투두 ID. 생성·조회 응답에서 받은 값을 투두 관련 API 경로에 사용합니다.", example = "1") Long id,
        @Schema(description = "태스크명(투두 이름)입니다.", example = "TODO API 설계") String title,
        @Schema(description = "AI 세부 분량 나누기 전 원본 태스크명. 일반 투두는 null일 수 있습니다.") String sourceTaskName,
        @Schema(description = "투두 메모. 입력한 내용이 없으면 null일 수 있습니다.") String memo,
        @Schema(description = "예정 시작 시각(UTC)", example = "2026-10-29T00:00:00Z") Instant startAt,
        @Schema(description = "마감 시각(UTC). 하루종일이면 다음 서울 날짜 00:00, 미포함", example = "2026-10-29T01:00:00Z") Instant dueAt,
        @Schema(description = "현재 목록·캘린더에 배정된 날짜. 이월 시 원래 시작 날짜와 다를 수 있음", example = "2026-10-29") LocalDate currentScheduleDate,
        @Schema(description = "하루종일 일정 여부입니다. true이면 날짜 중심으로 표시합니다.", example = "false") boolean allDay,
        @Schema(description = "현재 진행 상태. PENDING=진행 전, IN_PROGRESS=진행 중, PAUSED=일시정지, COMPLETED=완료입니다.") TodoStatus status,
        @Schema(description = "완료 시각. 미완료 또는 완료 취소 시 null") Instant completedAt,
        @Schema(description = "사용자가 직접 입력한 예상 작업 소요시간(분). AI 제안 수락 여부와 별도로 보존한 원본 값입니다.", example = "30") Integer userEstimatedDurationMinutes,
        @Schema(description = "예상시간 출처. USER_INPUT=사용자 직접 입력, RECURRENCE_TEMPLATE=반복 일정에서 복사입니다.") UserEstimateSource userEstimateSource,
        @Schema(description = "AI 예상시간(분). 제안이 없거나 입력 변경으로 무효화되면 null") Integer aiEstimatedDurationMinutes,
        @Schema(description = "최종 선택된 예상시간 종류. USER=직접 입력시간, AI=AI 제안시간입니다.") SelectedEstimateType selectedEstimateType,
        @Schema(description = "선택값에 따른 실제 적용 예상시간(분)", example = "30") Integer estimatedDurationMinutes,
        @Schema(description = "종료된 작업 세션의 실제 집중시간 합계(초). 실행 중 구간은 제외하며 기록이 없으면 0입니다.", example = "0") long actualDurationSeconds,
        @Schema(description = "현재 미완료 Todo의 마감 초과 여부. 이월 여부와 독립") boolean overdue,
        @Schema(description = "현재 배정 날짜가 서울 기준 원래 시작 날짜보다 뒤인지 여부") boolean rolledOver,
        @Schema(description = "반복 규칙 ID. 일반 Todo는 null", accessMode = Schema.AccessMode.READ_ONLY) Long recurrenceRuleId,
        @Schema(description = "불변 반복 회차 예정 시각. 일반 Todo는 null", accessMode = Schema.AccessMode.READ_ONLY) Instant recurrenceOccurrenceAt,
        @Schema(description = "불변 논리 회차 시작 날짜. 일반 Todo는 null", accessMode = Schema.AccessMode.READ_ONLY) LocalDate recurrencePeriodStartDate,
        @Schema(description = "생성 시각") Instant createdAt,
        @Schema(description = "최종 수정 시각") Instant updatedAt
) {
}
