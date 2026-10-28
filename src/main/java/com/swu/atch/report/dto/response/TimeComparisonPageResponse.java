package com.swu.atch.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.todo.domain.SelectedEstimateType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 제품의 예상·실제시간 비교. 전략별 실험 실행·연구용 정확도 지표는 포함하지 않음
@Schema(description = "제품의 예상·실제시간 비교. 전략별 실험 실행·연구용 정확도 지표는 포함하지 않음")
public record TimeComparisonPageResponse(
        @Schema(description = "실제 조회 범위의 시작 날짜(서울 기준, 포함)입니다.")
        LocalDate from,
        @Schema(description = "실제 조회 범위의 끝 날짜(서울 기준, 포함)입니다.")
        LocalDate to,
        @Schema(description = "조회한 항목 목록입니다.")
        List<TimeComparisonItem> items,
        @Schema(description = "현재 페이지 번호. 0이 첫 페이지입니다.")
        int page,
        @Schema(description = "요청에 적용된 페이지당 최대 항목 수입니다.")
        int size,
        @Schema(description = "조회 조건에 맞는 전체 항목 수입니다.")
        long totalElements,
        @Schema(description = "전체 페이지 수입니다.")
        int totalPages,
        @Schema(description = "다음 페이지가 있는지 여부입니다. true이면 page를 1 증가시켜 다음 목록을 조회할 수 있습니다.")
        boolean hasNext,
        @Schema(description = "조회·계산에 사용한 기준 시각(UTC)입니다. 새로 조회해도 보존된 계산 결과는 원래 기준 시각을 유지할 수 있습니다.")
        Instant asOf
) {
    public record TimeComparisonItem(
            @Schema(description = "서버가 발급한 투두 ID. 투두 생성·조회 응답에서 받은 값입니다.")
            Long todoId,
            @Schema(description = "태스크명(투두 이름)입니다.")
            String title,
            @Schema(description = "작업 완료 시각(UTC). 완료되지 않았으면 null입니다.")
            Instant completedAt,
            @Schema(description = "사용자가 직접 입력한 예상 작업 소요시간(분). AI 제안 수락 여부와 별도로 보존한 원본 값입니다.")
            int userEstimatedDurationMinutes,
            @Schema(description = "서버가 제안한 AI 예상 작업 소요시간(분). 제안이 없으면 null일 수 있습니다.")
            Integer aiEstimatedDurationMinutes,
            @Schema(description = "최종 선택된 예상시간 종류. USER=직접 입력시간, AI=AI 제안시간입니다.")
            SelectedEstimateType selectedEstimateType,
            @Schema(description = "종료된 작업 세션의 실제 집중시간 합계(초). 실행 중인 구간의 경과시간은 포함하지 않습니다.")
            long actualDurationSeconds,
            @Schema(description = "실제 초 - 사용자 예상 분*60. 음수 허용") long userErrorSeconds,
            @Schema(description = "실제 초 - AI 예상 분*60. AI 제안이 없으면 null") Long aiErrorSeconds) { }
}

