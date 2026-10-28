
package com.swu.atch.auth.controller;

import com.swu.atch.auth.dto.request.RefreshTokenRequest;
import com.swu.atch.auth.dto.response.TokenResponse;
import com.swu.atch.auth.service.AuthSessionService;
import com.swu.atch.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "03. [프론트] 인증 유지 · 로그아웃", description = "리프레시 토큰을 요청 본문으로 전달합니다.")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthSessionController {

    private final AuthSessionService authSessionService;

    public AuthSessionController(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    // 만료된 Access Token을 Refresh Token으로 갱신
    @Operation(
            summary = "Access·Refresh Token 갱신",
            description = """
            기존 Refresh Token을 이용하여 새 Access Token과 Refresh Token을 발급합니다.

            호출 조건:
            - Access Token 없이 호출 가능
            - Authorization 헤더를 첨부하지 마세요
            - 요청 본문에 현재 Refresh Token 전달

            성공 시:
            - Access Token과 Refresh Token을 모두 새 값으로 교체
            - Access Token 유효기간은 15분
            - Refresh Token의 최초 로그인 기준 14일 만료 시각은 유지
            - 기존 Refresh Token은 즉시 무효화

            프론트엔드 처리:
            - 200: 두 토큰을 안전하게 교체한 뒤 원래 API 요청 재시도
            - 401: 저장된 인증정보 삭제 후 로그인 화면 이동
            - 503: 서버 장애 안내, 즉시 재로그인 강제 금지
            - 동시에 여러 요청이 만료되어도 갱신 요청은 한 번만 수행

            ### 주의사항 이미 사용한 Refresh Token을 다시 보내면
            재사용으로 감지되어 현재 인증 세션이 폐기될 수 있습니다.
            """
    )
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "토큰 갱신 성공",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류 - VALIDATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "세션 만료·폐기·토큰 재사용 - REFRESH_SESSION_INVALID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "토큰 갱신 후 사용자 조회 또는 JWT 발급 실패. 재로그인이 필요할 수 있음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Redis 연결 장애 - AUTH_SERVICE_UNAVAILABLE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/token/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authSessionService.refresh(request.refreshToken()));
    }

    // 현재 Refresh Session 폐기
    @Operation(
            summary = "현재 기기 로그아웃",
            description = """
            전달한 Refresh Token이 속한 인증 세션을 폐기합니다.

            - Access Token 없이 호출 가능
            - Authorization 헤더를 첨부하지 마세요
            - 요청 본문에 현재 Refresh Token 전달
            - 성공 시 HTTP 204, 응답 본문 없음
            - 이미 만료되거나 삭제된 세션도 204 반환
            - 다른 기기의 로그인 세션에는 영향 없음

            프론트엔드 처리:
            - 서버 로그아웃 완료 후 Keychain의 두 토큰 삭제
            - 로그인 화면으로 이동
            - 503에서는 서버 세션 폐기가 확인되지 않았으므로 사용자에게 안내

            ### 주의사항 기존 Access Token 자체는 서버에서 즉시 폐기하지 않으며
            남은 유효기간 동안 사용 가능할 수 있습니다.
            """
    )
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "로그아웃 완료 또는 이미 폐기된 세션"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Refresh Token 누락 - VALIDATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Redis 연결 장애 - AUTH_SERVICE_UNAVAILABLE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authSessionService.logout(request.refreshToken());
    }
}
