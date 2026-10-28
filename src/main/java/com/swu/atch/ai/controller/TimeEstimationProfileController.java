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
import com.swu.atch.ai.dto.response.TimeEstimationProfileResponse;

// 명세 전용 opt-in. 기본 실행에는 등록하지 않으며 도메인 구현 완료 시 조건과 501을 제거한다.
// JWT의 본인 ID·credential_stamp를 후속 Service에 전달한다. 현재 DB/Redis/외부 AI를 호출하지 않는다.
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "app.api.contracts.enabled", havingValue = "true")
@Tag(name = "개인화 Profile", description = "FR-AI-001~006. 통계 계산 Service는 후속 구현 [명세 단계: 현재 501]")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "필수값·형식·단일 필드 범위 오류. 관계·상태 검증은 후속 Service", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "501", description = "현재 명세 단계. Service 미연결 - NOT_IMPLEMENTED", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증 기반 장애. 후속 구현에서는 저장소 장애도 구분", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TimeEstimationProfileController {
    private static final String CONTRACT_STAGE = "[명세 단계·현재 501] 아래 성공 동작과 비즈니스 오류는 후속 구현 계약입니다.\n\n";

    @GetMapping(value = "/ai/profile", produces = "application/json")
    @Operation(summary = "저장된 개인화 Profile 조회", description = CONTRACT_STAGE + "저장본과 새 계산을 구분합니다. 유효 이력 없는 신규 사용자는 NO_HISTORY·계수1.0·표본0·LOW이며 404가 아닙니다. 저장 updatedAt을 이번 계산 기준 시각으로 오해하지 않도록 calculatedAsOf는 근거 없으면 null입니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] 저장된 개인화 Profile 조회", content = @Content(schema = @Schema(implementation = TimeEstimationProfileResponse.class)))
    public TimeEstimationProfileResponse profile(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        throw notImplemented();
    }

    @PostMapping(value = "/ai/profile/recalculate", produces = "application/json")
    @Operation(summary = "개인화 Profile 재계산", description = CONTRACT_STAGE + "미삭제 COMPLETED·USER_INPUT·양수 예상 분·종료 구간 실제 초·완료 시각 <= asOf만 사용합니다. 실제 분은 초/60.0, 반감기30일·prior strength3을 유지합니다. 유효 기록 없는 신규 행은 만들지 않습니다. RECURRENCE_TEMPLATE은 제외합니다. Baseline은 시간 예측 내부 계산이며 추가 공개 API를 만들지 않습니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] 개인화 Profile 재계산", content = @Content(schema = @Schema(implementation = TimeEstimationProfileResponse.class)))
    public TimeEstimationProfileResponse recalculate(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        throw notImplemented();
    }

    private ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "개인화 Profile Service 연결 전입니다.");
    }
}

