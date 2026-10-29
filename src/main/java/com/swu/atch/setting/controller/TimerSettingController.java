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
import com.swu.atch.session.service.TimerSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.swu.atch.setting.dto.request.TimerSettingsUpdateRequest;
import com.swu.atch.setting.dto.response.TimerSettingsResponse;

// 인증된 사용자와 서버 시각을 기준으로 처리한다. 외부 AI는 호출하지 않는다.
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "04. [프론트] 작업 세션 · 타이머")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "필수값·형식·범위·관계 검증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "JWT 미인증·삭제 계정·이전 비밀번호 상태", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증 기반 장애 또는 일일 확정 복구 대기", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TimerSettingController {
    private final TimerSettingService service;

    @GetMapping(value = "/settings/timer", produces = "application/json")
    @Operation(summary = "작업 전환 제한 설정 조회", description = """
            작업 전환 제한 설정 화면을 열 때 호출합니다.

            - `taskSwitchRestrictionEnabled`: 전환 제한 사용 여부입니다.
            - `taskSwitchWaitMinutes`: 전환 대기 시간(분)입니다.
            - 기본값은 제한 사용·10분입니다. 반복 추천 설정과는 별개입니다.
            """)
    @ApiResponse(responseCode = "200", description = "타이머 설정 조회", content = @Content(schema = @Schema(implementation = TimerSettingsResponse.class)))
    public TimerSettingsResponse get(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return service.get(id(jwt), stamp(jwt));
    }

    @PatchMapping(value = "/settings/timer", consumes = "application/json", produces = "application/json")
    @Operation(summary = "작업 전환 제한·대기 시간 변경", description = """
            바꿀 필드만 보냅니다. null이거나 생략한 필드는 기존 값을 유지하며 적어도 한 필드는 필요합니다.

            대기 시간은 0보다 큰 분 단위 정수입니다. 변경 사항은 새 전환 요청부터 적용되며 이미 시작한 대기의 `readyAt`은 바뀌지 않습니다.
            """)
    @ApiResponse(responseCode = "200", description = "타이머 설정 부분 변경", content = @Content(schema = @Schema(implementation = TimerSettingsResponse.class)))
    public TimerSettingsResponse update(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TimerSettingsUpdateRequest.class),
                            examples = @ExampleObject(name = "요청 예시", value = "{\"taskSwitchRestrictionEnabled\":true,\"taskSwitchWaitMinutes\":10}")))
            @Valid @RequestBody TimerSettingsUpdateRequest request
    ) {
        if (request.taskSwitchRestrictionEnabled() == null && request.taskSwitchWaitMinutes() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "변경할 설정이 필요합니다.");
        return service.update(id(jwt), stamp(jwt), request);
    }

    private Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private String stamp(Jwt jwt) { return jwt.getClaimAsString("credential_stamp"); }
}

