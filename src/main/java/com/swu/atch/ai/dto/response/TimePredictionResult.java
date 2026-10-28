package com.swu.atch.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.swu.atch.ai.domain.PredictionReasonCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 작업별 제안. Claude 보정 성공만 1회 사용으로 소비하고 Baseline 폴백은 재시도 허용
@Schema(description = "작업별 제안. Claude 보정 성공만 1회 사용으로 소비하고 Baseline 폴백은 재시도 허용")
public record TimePredictionResult(
        @Schema(description = "이 작업에 연결된 서버 발급 AI 초안 UUID. 후속 예측·저장 요청에서 같은 값을 사용합니다.")
        UUID draftId,
        @Schema(description = "연결된 본인 투두 ID. 저장 전 초안은 null입니다.")
        Long todoId,
        @Schema(description = "사용자가 직접 입력한 예상 작업 소요시간(분). AI 제안 수락 여부와 별도로 보존한 원본 값입니다.")
        int userEstimatedDurationMinutes,
        @Schema(description = "사용자 시간 보정 배수. 직접 입력시간에 곱해 평소 예상 습관을 반영합니다. 유효 이력이 없으면 1.0입니다.")
        double durationCorrectionFactor,
        @Schema(description = "기본 보정시간(분). 사용자 입력시간에 개인화 보정 배수를 곱한 값입니다.")
        double baselinePredictionMinutes,
        @Schema(description = "최종 제안시간(분). Claude 성공 여부와 관계없이 값을 반환하므로 mode와 claudeStatus로 통계 대체 결과인지 확인합니다.")
        int aiEstimatedDurationMinutes,
        @Schema(description = "Claude가 반영한 작업 맥락 보정 배수입니다.")
        double contextAdjustmentFactor,
        @Schema(description = "예측 방식. HYBRID=개인화 통계와 Claude 맥락 보정, STATISTICAL_BASELINE=개인화 통계 기반 대체 결과입니다.")
        Mode mode,
        @Schema(description = "Claude 처리 결과. APPLIED=반영, DISABLED=비활성, TIMEOUT=시간 초과, RATE_LIMITED=호출 제한, INVALID_RESPONSE=형식 오류, REFUSED=거절, TRUNCATED=응답 잘림, UNAVAILABLE=이용 불가입니다.")
        ClaudeStatus claudeStatus,
        @Schema(description = "유사 작업 검색 상태. AVAILABLE=검색 가능, PARTIAL=일부 기록만 준비됨, NO_HISTORY=유효 이력 없음, DISABLED=비활성, UNAVAILABLE=검색 이용 불가입니다.")
        SimilarTodoSearchResponse.VoyageStatus voyageStatus,
        @Schema(description = "시간 보정 이유 코드 목록. 화면 문구 예시:\n\n- `INSUFFICIENT_HISTORY`: 완료 이력 부족\n- `USER_TENDS_TO_UNDERESTIMATE`: 평소 예상보다 오래 걸림\n- `USER_TENDS_TO_OVERESTIMATE`: 평소 예상보다 일찍 끝남\n- `ESTIMATION_PATTERN_STABLE`: 예상 패턴 안정\n- `HIGH_ESTIMATION_VARIABILITY`: 예상 오차 변동 큼\n- `NO_SIMILAR_TASK_HISTORY`: 유사 작업 이력 없음\n- `SIMILAR_TASKS_TOOK_LONGER`: 유사 작업이 더 오래 걸림\n- `SIMILAR_TASKS_TOOK_LESS_TIME`: 유사 작업이 더 짧게 걸림\n- `CURRENT_TASK_SCOPE_LARGER`: 현재 작업 범위가 더 큼\n- `CURRENT_TASK_SCOPE_SMALLER`: 현재 작업 범위가 더 작음\n- `CURRENT_TASK_HAS_MORE_STEPS`: 현재 작업의 단계가 더 많음\n- `BASELINE_RETAINED`: 기본 보정시간 유지\n- `CONTEXT_ADJUSTED_UP`: 작업 맥락으로 시간 증가\n- `CONTEXT_ADJUSTED_DOWN`: 작업 맥락으로 시간 감소")
        List<PredictionReasonCode> reasonCodes,
        @Schema(description = "예측에 참고한 본인의 유사 완료 투두 목록입니다.")
        List<SimilarTodoSummary> similarTodos,
        @Schema(description = "이 작업의 AI 기능 사용 상태·재시도 가능 여부입니다.")
        AiUsageResponse usage,
        @Schema(description = "새로운 AI 호출 없이 보존 결과를 반환했는지 여부입니다.")
        boolean cached,
        @Schema(description = "해당 결과 계산의 기준 시각. 재시도 묶음의 캐시 항목도 원래 시각 유지") Instant asOf,
        @Schema(description = "예측에 사용한 개인화 시간 예측 프로필입니다.")
        TimeEstimationProfileResponse profile
) {
    public enum Mode { HYBRID, STATISTICAL_BASELINE }
    public enum ClaudeStatus { APPLIED, DISABLED, TIMEOUT, RATE_LIMITED, INVALID_RESPONSE, REFUSED, TRUNCATED, UNAVAILABLE }
}

