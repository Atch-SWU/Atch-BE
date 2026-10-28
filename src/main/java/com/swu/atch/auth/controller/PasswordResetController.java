package com.swu.atch.auth.controller;

import com.swu.atch.auth.dto.request.*;
import com.swu.atch.auth.dto.response.PasswordResetProofResponse;
import com.swu.atch.auth.service.PasswordResetService;
import com.swu.atch.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/password-reset")
@Tag(name = "비밀번호 재설정", description = "LOCAL 계정의 이메일 재인증 후 재설정")
@SecurityRequirements
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "입력·코드·재설정 증명 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증 기반 장애 또는 발송 대기열 포화", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class PasswordResetController {
    private final PasswordResetService service;

    @PostMapping("/code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "재설정 코드 요청", description = "등록·미등록 이메일 모두 같은 204 응답. 계정이 존재하면 별도 처리로 발송합니다. 응답은 발송 완료를 보장하지 않습니다. 코드 5분·재전송 60초·오입력 최대 5회. SMTP 실패도 응답에 노출하지 않습니다. 메일이 오지 않으면 60초 후 재요청하세요.")
    @ApiResponse(responseCode = "204", description = "요청 접수. 계정 존재 여부·SMTP 결과와 무관하며 본문 없음")
    @ApiResponse(responseCode = "429", description = "60초 재전송 제한", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void requestCode(@Valid @RequestBody PasswordResetCodeRequest request) {
        service.requestCode(request.email());
    }

    @PostMapping("/verify")
    @Operation(summary = "재설정 코드 확인", description = "코드를 원자적으로 소비하고 10분 유효한 일회용 resetToken을 반환합니다. 가입용 verificationToken과 교환할 수 없습니다.")
    @ApiResponse(responseCode = "200", description = "재설정 증명 발급", content = @Content(schema = @Schema(implementation = PasswordResetProofResponse.class)))
    @ApiResponse(responseCode = "429", description = "코드 오입력 5회", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PasswordResetProofResponse verify(@Valid @RequestBody PasswordResetVerifyRequest request) {
        var proof = service.verify(request.email(), request.code());
        return new PasswordResetProofResponse(proof.resetToken(), proof.expiresIn());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "새 비밀번호로 재설정", description = "email·resetToken·newPassword를 전달합니다. 증명은 단회 소비되며 모든 기기에서 재로그인해야 합니다. 처리 실패 시 인증부터 다시 진행하고, 응답 유실 시 새 비밀번호로 로그인해 결과를 확인하세요.")
    @ApiResponse(responseCode = "204", description = "비밀번호 재설정 완료. 자동 로그인하지 않음")
    public void reset(@Valid @RequestBody PasswordResetRequest request) {
        service.reset(request.email(), request.resetToken(), request.newPassword());
    }
}
