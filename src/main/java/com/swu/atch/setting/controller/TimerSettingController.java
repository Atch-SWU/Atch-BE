package com.swu.atch.setting.controller;

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
import com.swu.atch.setting.dto.request.TimerSettingsUpdateRequest;
import com.swu.atch.setting.dto.response.TimerSettingsResponse;

// 명세 전용 opt-in. 기본 실행에는 등록하지 않으며 도메인 구현 완료 시 조건과 501을 제거한다.
// JWT의 본인 ID·credential_stamp를 후속 Service에 전달한다. 현재 DB/Redis/외부 AI를 호출하지 않는다.
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "app.api.contracts.enabled", havingValue = "true")
@Tag(name = "타이머 설정", description = "FR-SETTING-001 및 FR-SESS-013 [명세 단계: 현재 501]")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "필수값·형식·단일 필드 범위 오류. 관계·상태 검증은 후속 Service", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "501", description = "현재 명세 단계. Service 미연결 - NOT_IMPLEMENTED", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증 기반 장애. 후속 구현에서는 저장소 장애도 구분", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TimerSettingController {
    private static final String CONTRACT_STAGE = "[명세 단계·현재 501] 아래 성공 동작과 비즈니스 오류는 후속 구현 계약입니다.\n\n";

    @GetMapping(value = "/settings/timer", produces = "application/json")
    @Operation(summary = "타이머 설정 조회", description = CONTRACT_STAGE + "반복 추천 설정과 분리합니다. 기존 기본값은 전환 제한 활성·대기 10분입니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] 타이머 설정 조회", content = @Content(schema = @Schema(implementation = TimerSettingsResponse.class)))
    public TimerSettingsResponse get(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        throw notImplemented();
    }

    @PatchMapping(value = "/settings/timer", consumes = "application/json", produces = "application/json")
    @Operation(summary = "타이머 설정 부분 변경", description = CONTRACT_STAGE + "null 필드는 유지하며 적어도 한 항목이 필요합니다. 대기 분은 양수입니다. 이미 시작한 대기의 readyAt은 바꾸지 않고 새 전환부터 반영합니다.")
    @ApiResponse(responseCode = "200", description = "[구현 후] 타이머 설정 부분 변경", content = @Content(schema = @Schema(implementation = TimerSettingsResponse.class)))
    public TimerSettingsResponse update(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimerSettingsUpdateRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"taskSwitchRestrictionEnabled\":true,\"taskSwitchWaitMinutes\":10}")))
            @Valid @RequestBody TimerSettingsUpdateRequest request
    ) {
        if (request.taskSwitchRestrictionEnabled() == null && request.taskSwitchWaitMinutes() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "변경할 설정이 필요합니다.");
        throw notImplemented();
    }

    private ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "타이머 설정 Service 연결 전입니다.");
    }
}

