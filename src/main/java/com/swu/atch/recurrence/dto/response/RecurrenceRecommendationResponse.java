package com.swu.atch.recurrence.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.List;
import com.swu.atch.recurrence.domain.*;

@Schema(description = "반복 등록 추천 항목")
public record RecurrenceRecommendationResponse(
        @Schema(description = "서버가 발급한 추천 패턴 ID. 추천 선택 API 경로에 사용합니다.")
        Long patternId,

        @Schema(description = "감지된 반복 태스크명입니다.")
        String taskName,

        @Schema(description = "주기. DAILY=매일, WEEKLY=매주입니다.")
        RecurrenceFrequency frequency,

        @Schema(description = "WEEKLY의 반복 요일. DAILY는 null입니다.")
        DayOfWeek dayOfWeek,

        @Schema(description = "하루종일 반복 여부입니다.")
        boolean allDay,

        @Schema(description = "서울 기준 예정 시작 시각. 하루종일은 null입니다.")
        LocalTime scheduledTime,

        @Schema(description = "최신 일/주 연속 구간의 서로 다른 발생 날짜 수입니다.")
        int occurrenceCount,

        @Schema(description = "가장 최근 관측의 서울 기준 예정 시작 날짜입니다.")
        LocalDate lastOccurrenceDate,

        @Schema(description = "현재 추천 상태로 전환된 시각(UTC). 목록 재조회만으로 갱신하지 않습니다.")
        Instant lastSuggestedAt,

        @Schema(description = "마지막 수정 시각(UTC). 후속 변경 요청의 expectedUpdatedAt에 복사합니다.")
        Instant updatedAt,

        @Schema(description = "등록 폼에 사용할 기본값 제안입니다.")
        RecurrenceTemplateSuggestion templateSuggestion
) {
}
