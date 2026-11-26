package com.swu.atch.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 일간 집계. 배정 지표는 과거 Snapshot/오늘 현재 상태, 완료 건수는 현재 유효 완료 Todo 기준
@Schema(description = "일간 집계. 배정 지표는 과거 Snapshot/오늘 현재 상태, 완료 건수는 현재 유효 완료 Todo 기준")
public record DailyReportResponse(
        @Schema(description = "조회 대상 날짜(서울 기준)입니다.")
        LocalDate date,
        @Schema(description = "통계 상태. LIVE=오늘 실시간, FINALIZED=과거 확정, PENDING_FINALIZATION=확정 대기, UNAVAILABLE=자료 없음입니다. 미확정 값의 null을 0으로 표시하지 않습니다.")
        State state,
        @Schema(description = "확정 또는 현재 배정 수. 미확정·자료 없음은 null") Long assignedCount,
        @Schema(description = "배정된 투두 중 완료한 투두 수입니다.")
        Long completedAssignedCount,
        @Schema(description = "0~1 비율. 배정 0개 또는 미확정이면 null") Double completionRate,
        @Schema(description = "해당 날짜 완료 시각을 가진 현재 유효한 완료 Todo 수") Long completedTodoCount,
        @Schema(description = "배정된 투두 중 마감이 초과된 투두 수입니다.")
        Long overdueAssignedCount,
        @Schema(description = "종료된 작업 세션을 기준으로 집계한 집중시간(초)입니다.")
        Long closedFocusDurationSeconds,
        @Schema(description = "실행 중인 작업 세션에서 기준 시각까지 임시 집계한 집중시간(초)입니다.")
        Long liveFocusDurationSeconds,
        @Schema(description = "일시정지 횟수입니다.")
        Long pauseCount,
        @Schema(description = "일시정지 또는 전환 종료 이후 작업을 재개한 횟수입니다. 최초 시작은 포함하지 않습니다.")
        Long resumeCount,
        @Schema(description = "조회·계산에 사용한 기준 시각(UTC)입니다. 새로 조회해도 보존된 계산 결과는 원래 기준 시각을 유지할 수 있습니다.")
        Instant asOf,
        @Schema(description = "서울 기준 두 시간 구간 12개를 0시부터 순서대로 반환합니다. 기록이 없는 구간은 0초입니다.")
        List<FocusBucket> focusBuckets,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "일간 리포트에서 제공되는 작업별 집중 기록과 집중 시간 요약입니다. 주간 days 항목에서는 생략합니다.")
        FocusDetails focusDetails
) {
    public enum State { LIVE, FINALIZED, PENDING_FINALIZATION, UNAVAILABLE }

    public record FocusBucket(
            @Schema(description = "서울 기준 구간 시작 시각의 시 값입니다. 예를 들어 10은 10:00~12:00 구간입니다.", example = "10")
            int startHour,
            @Schema(description = "이 구간에 해당하는 종료 세션의 집중시간 합계(초)입니다.")
            long closedDurationSeconds,
            @Schema(description = "이 구간에 해당하는 실행 중 세션의 임시 집중시간 합계(초)입니다.")
            long liveDurationSeconds
    ) { }

    public record FocusDetails(
            @Schema(description = "가장 오래 집중한 두 시간 구간의 시작 시각입니다. 동률이면 이른 구간이며 집중시간이 없으면 null입니다.", nullable = true)
            Integer peakFocusStartHour,
            @Schema(description = "그날의 총 집중시간을 양의 집중시간을 기록한 작업 수로 나눈 값(초)입니다. 해당 작업이 없으면 null입니다.", nullable = true)
            Double averageFocusPerTodoSeconds,
            @Schema(description = "조회 날짜에 생성됐고 해당 날짜에 집중시간 또는 일시정지 기록이 있는 작업을 작업별로 한 행에 모아 첫 기록 시각 순으로 반환합니다.")
            List<FocusTodo> todos
    ) { }

    public record FocusTodo(
            @Schema(description = "집중 기록이 연결된 투두의 ID입니다.")
            Long todoId,
            @Schema(description = "투두의 현재 제목입니다. 삭제된 작업은 null입니다.", nullable = true)
            String currentTitle,
            @Schema(description = "조회 날짜에 포함되는 첫 집중 구간의 시작 또는 첫 일시정지 시각(UTC)입니다. 전날부터 이어진 구간은 조회 날짜의 시작 시각입니다.")
            Instant firstActivityAt,
            @Schema(description = "이 작업이 조회 날짜에 기록한 종료 세션의 집중시간 합계(초)입니다.")
            long closedFocusDurationSeconds,
            @Schema(description = "이 작업이 조회 날짜에 기록한 실행 중 세션의 임시 집중시간 합계(초)입니다.")
            long liveFocusDurationSeconds,
            @Schema(description = "이 작업을 조회 날짜에 일시정지한 횟수입니다.")
            long pauseCount
    ) { }
}

