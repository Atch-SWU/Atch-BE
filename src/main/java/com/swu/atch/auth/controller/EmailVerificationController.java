package com.swu.atch.auth.controller;

import com.swu.atch.auth.dto.request.EmailCodeRequest;
import com.swu.atch.auth.dto.request.EmailCodeVerifyRequest;
import com.swu.atch.auth.service.EmailVerificationService;
import com.swu.atch.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// 이메일 인증 컨트롤러
@Tag(name = "01. [프론트] 회원가입 이메일 인증", description = "로그인 없이 호출합니다. 코드 발송 → 코드 확인 → 회원가입 순서입니다.")
@RestController
@RequestMapping("/api/v1/auth/email")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationController(
            EmailVerificationService emailVerificationService
    ) {
        this.emailVerificationService = emailVerificationService;
    }

    // 이메일 인증코드 발송
    @Operation(
            summary = "이메일 인증코드 발송",
            description = """
            회원가입할 이메일로 6자리 인증코드를 발송합니다.

            - 로그인 불필요
            - 인증코드 유효기간: 5분
            - 동일 이메일 재전송 제한: 60초
            - 성공 시 응답 본문 없음
            - 이메일 발송 후 인증코드 입력 화면으로 이동
            """
    )
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "인증코드 발송 성공"),
            @ApiResponse(responseCode = "400", description = "이메일 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "재전송 제한 - EMAIL_RESEND_TOO_SOON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "메일 발송 실패 - EMAIL_SEND_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendCode(@Valid @RequestBody EmailCodeRequest request) {
        emailVerificationService.sendCode(request.email());
    }

    // 인증코드 확인 및 회원가입 증명 발급
    @Operation(
            summary = "이메일 인증코드 확인",
            description = """
            이메일로 수신한 6자리 인증코드를 검증합니다.

            - 로그인 불필요
            - 잘못 입력한 횟수는 최대 5회
            - 성공하면 verificationToken과 expiresIn 반환
            - verificationToken은 6자리 인증코드와 다른 값
            - 반환된 verificationToken을 회원가입 API에 전달
            - 인증코드는 성공 즉시 무효화되며 재사용 불가
            - verificationToken은 10분간 유효하고 한 번만 사용 가능
            """
    )
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공 및 회원가입 증명 발급"),
            @ApiResponse(responseCode = "400", description = "인증코드 오류 또는 만료 - EMAIL_CODE_INVALID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "오입력 5회 초과 - EMAIL_CODE_ATTEMPTS_EXCEEDED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/verify")
    public EmailVerificationService.VerifiedEmail verifyCode(
            @Valid @RequestBody EmailCodeVerifyRequest request
    ) {
        return emailVerificationService.verifyCode(
                request.email(),
                request.code()
        );
    }
}
