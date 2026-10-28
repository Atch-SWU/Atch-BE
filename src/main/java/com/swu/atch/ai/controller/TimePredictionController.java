package com.swu.atch.ai.controller;

import com.swu.atch.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.swu.atch.ai.dto.request.*;
import com.swu.atch.ai.dto.response.*;

// 명세 전용 opt-in. 기본 실행에는 등록하지 않으며 도메인 구현 완료 시 조건과 501을 제거한다.
// JWT의 본인 ID·credential_stamp를 후속 Service에 전달한다. 현재 DB/Redis/외부 AI를 호출하지 않는다.
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "app.api.contracts.enabled", havingValue = "true")
@Tag(name = "AI 시간 예측", description = "FR-AI-001~006. 작업당 성공1회, 실패·Baseline 폴백은 재시도 가능 [명세 단계: 현재 501]")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "필수값·형식·단일 필드 범위 오류. 관계·상태 검증은 후속 Service", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "501", description = "현재 명세 단계. Service 미연결 - NOT_IMPLEMENTED", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증 기반 장애. 후속 구현에서는 저장소 장애도 구분", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TimePredictionController {
    private static final String CONTRACT_STAGE = "[명세 단계·현재 501] 아래 성공 동작과 비즈니스 오류는 후속 구현 계약입니다.\n\n";

    @PostMapping(value = "/ai/drafts", consumes = "application/json", produces = "application/json")
    @Operation(summary = "AI 작업 초안 식별자 발급", description = CONTRACT_STAGE + "저장 전 신규 작업·수동 추가 세부 작업에 서버 draftId를 발급합니다. 기존 todoId는 본인 소유와 영속 사용 이력에 연결합니다. 분해는 생성 단계 전용이며 기존 Todo는 NOT_APPLICABLE입니다. 임의 새 식별자로 기존 Todo의 성공 횟수를 초기화하지 않습니다. 초안 발급에도 별도 사용자별 제한을 적용합니다. 임시 결과 만료와 성공 사용 이력 보존은 구분하며 실제 영속 방식은 후속 설계입니다.")
    @ApiResponse(responseCode = "201", description = "[구현 후] AI 작업 초안 식별자 발급", content = @Content(schema = @Schema(implementation = AiDraftResponse.class)))
    @ApiResponse(responseCode = "429", description = "[구현 후] 사용자별 외부 호출 제한. 재시도 시각은 Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public AiDraftResponse draft(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AiDraftRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{}")))
            @Valid @RequestBody AiDraftRequest request
    ) {
        throw notImplemented();
    }

    @PostMapping(value = "/ai/time-predictions", consumes = "application/json", produces = "application/json")
    @Operation(summary = "작업 시간 보정 제안", description = CONTRACT_STAGE + "사용자 입력 분×개인화 계수=Baseline, Claude는 유한한 0.75~1.40 맥락 계수·기존 이유 코드만 제공합니다. 최종 분은 서버에서 반올림·양수 범위를 보장합니다. 동일 draft/Todo의 Claude 구조화 보정 성공은1회만 소비합니다. 동일 입력 재요청은 보존 결과를 반환하고 다른 입력은409이며 편집·거절·토큰 만료로 초기화하지 않습니다. 실패·잘림·형식 오류·미활성·Baseline 폴백은 성공을 소비하지 않습니다. 제안 토큰은10분이며 동일 성공 결과에 대한 재발급은 외부 재호출과 구분합니다. 일반 Todo 생성·수정은 외부 실패와 독립입니다. 실제 횟수·예약·캐시는 아직 미구현입니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] 작업 시간 보정 제안", content = @Content(schema = @Schema(implementation = TimePredictionResponse.class)))
    @ApiResponse(responseCode = "409", description = "[구현 후] AI 호출 진행 중·이미 성공한 작업의 다른 입력·만료 또는 입력이 다른 제안", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "[구현 후] 사용자별 외부 호출 제한. 재시도 시각은 Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TimePredictionResponse predict(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimePredictionRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"draftId\":\"11111111-1111-4111-8111-111111111111\",\"title\":\"기획안 초안 작성\",\"sourceTaskName\":null,\"memo\":null,\"userEstimatedDurationMinutes\":30}")))
            @Valid @RequestBody TimePredictionRequest request
    ) {
        throw notImplemented();
    }

    @PostMapping(value = "/ai/time-predictions/batch", consumes = "application/json", produces = "application/json")
    @Operation(summary = "세부 작업별 시간 보정 제안", description = CONTRACT_STAGE + "모든 세부 시간 입력 후 요청합니다. 작업별 사용자 입력시간을 보존하고 각각의 Baseline·맥락 계수로 보정하며 합계는 서버가 계산합니다. 같은 사용자·원본 초안에 속한 중복 없는 세부 draftId를 확인합니다. 각 작업마다 성공1회 정책을 적용하고 이미 성공한 항목은 동일 입력이면 재사용합니다. 실패 항목만 재시도할 수 있으며 묶음 요청으로 제한을 우회할 수 없습니다. AI가 직접 전체 시간을 정하거나 합계 시간을 비율로 분배하지 않습니다. 외부 요청 중 DB 트랜잭션·사용자 잠금을 유지하지 않습니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] 세부 작업별 시간 보정 제안", content = @Content(schema = @Schema(implementation = TimePredictionBatchResponse.class)))
    @ApiResponse(responseCode = "409", description = "[구현 후] AI 호출 진행 중·이미 성공한 작업의 다른 입력·만료 또는 입력이 다른 제안", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "[구현 후] 사용자별 외부 호출 제한. 재시도 시각은 Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public TimePredictionBatchResponse predictBatch(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimePredictionBatchRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"decompositionDraftId\":\"11111111-1111-4111-8111-111111111111\",\"sourceTaskName\":\"기획안 초안 작성\",\"memo\":null,\"tasks\":[{\"draftId\":\"22222222-2222-4222-8222-222222222222\",\"title\":\"핵심 메시지 한 줄 정하기\",\"userEstimatedDurationMinutes\":10}]}")))
            @Valid @RequestBody TimePredictionBatchRequest request
    ) {
        throw notImplemented();
    }

    private ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "AI 시간 예측 Service 연결 전입니다.");
    }
}

