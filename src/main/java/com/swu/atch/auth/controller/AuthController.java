
package com.swu.atch.auth.controller;

import com.swu.atch.auth.dto.request.LoginRequest;
import com.swu.atch.auth.dto.request.SignUpRequest;
import com.swu.atch.auth.dto.response.TokenResponse;
import com.swu.atch.auth.service.LocalAuthService;
import com.swu.atch.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "01. [프론트] 인증")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LocalAuthService localAuthService;

    public AuthController(LocalAuthService localAuthService) {
        this.localAuthService = localAuthService;
    }

    // 이메일 회원가입 및 자동 로그인
    @Operation(
            summary = "이메일 회원가입 및 자동 로그인",
            description = """
            이메일 인증 완료 후 회원가입을 진행하고 자동 로그인합니다.

            ### 처리 결과

            - USER·AUTH_ACCOUNT·USER_SETTING 생성
            - Access Token 및 Refresh Token 반환
            - 성공 시 로그인 화면을 거치지 않고 앱 진입

            ### 오류 시 화면 처리

            - EMAIL_VERIFICATION_REQUIRED: 이메일 재인증 필요
            - EMAIL_ALREADY_REGISTERED: 기존 계정으로 로그인
            - SIGNUP_FAILED_REVERIFY: 가입 여부 확인 후 필요하면 이메일 재인증
            - SIGNUP_COMPLETED_LOGIN_FAILED: 가입 완료, 로그인 화면으로 이동

            ### 주의사항

            - 회원가입 요청이 실패했다고 무조건 재전송하지 마세요
            - Verification Token은 일회용입니다
            - 응답 자체를 받지 못했다면 가입 성공 여부가 불확실할 수 있습니다
            """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SignUpRequest.class),
                            examples = @ExampleObject(name = "이메일 인증 후 회원가입", value = """
                                    {
                                      "email": "user@example.com",
                                      "password": "Atch123!",
                                      "nickname": "엣취사용자",
                                      "verificationToken": "verificationTokenFromVerifyResponse",
                                      "deviceId": "550e8400-e29b-41d4-a716-446655440000"
                                    }
                                    """)))
    )
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공 및 자동 로그인 토큰 발급",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력값 오류 또는 이메일 인증 증명 무효 - EMAIL_VERIFICATION_REQUIRED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 가입된 이메일 - EMAIL_ALREADY_REGISTERED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "회원가입 DB 처리 실패 또는 결과 확인 불가 - SIGNUP_FAILED_REVERIFY",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "계정 생성 완료, 자동 로그인 실패 - SIGNUP_COMPLETED_LOGIN_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return localAuthService.signUp(request);
    }

    // 이메일·비밀번호 로그인
    @Operation(
            summary = "이메일 로그인",
            description = """
            가입된 LOCAL 계정의 이메일과 비밀번호를 검증합니다.

            - 로그인 불필요
            - 이메일 또는 비밀번호 불일치 시 동일한 오류 응답 반환
            - 로그인 성공 시 마지막 로그인 시각 갱신
            - Access Token 15분 및 Refresh Token 14일 발급
            - accessToken은 API Authorization 헤더에 Bearer 방식으로 사용
            - refreshToken은 iOS Keychain에 저장하고 추후 토큰 갱신 시 사용
            """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = LoginRequest.class),
                            examples = @ExampleObject(name = "이메일 로그인", value = """
                                    {
                                      "email": "user@example.com",
                                      "password": "Atch123!",
                                      "deviceId": "550e8400-e29b-41d4-a716-446655440000"
                                    }
                                    """)))
    )
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공 및 인증 토큰 발급",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 오류 - INVALID_CREDENTIALS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "로그인 처리 중 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return localAuthService.login(request);
    }
}
