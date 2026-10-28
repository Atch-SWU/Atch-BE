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
@Tag(name = "04. [프론트] 비밀번호 재설정", description = "로그인 없이 이메일 재인증으로 비밀번호를 재설정합니다.")
@SecurityRequirements
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "입력·코드·재설정 증명 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "인증 기반 장애 또는 발송 대기열 포화", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class PasswordResetController {
    private final PasswordResetService service;

    @PostMapping("/code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "재설정 코드 요청", description = """
            ### 호출 순서
            로그인하지 못하는 사용자가 이메일을 입력하면 호출합니다. 요청 후 인증 코드 입력 화면으로 이동합니다.

            ### 응답·재요청

            - 등록 여부와 관계없이 본문 없는 204를 반환합니다. 응답이 메일 발송 완료를 뜻하지는 않습니다.
            - 코드 유효시간은 5분, 재전송 간격은 60초, 잘못 입력한 횟수는 최대 5회입니다.
            - 메일이 오지 않으면 60초 뒤 재요청할 수 있습니다. 메일 발송 실패 여부는 응답에 노출하지 않습니다.
            """)
    @ApiResponse(responseCode = "204", description = "요청 접수. 계정 존재 여부·SMTP 결과와 무관하며 본문 없음")
    @ApiResponse(responseCode = "429", description = "60초 재전송 제한", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void requestCode(@Valid @RequestBody PasswordResetCodeRequest request) {
        service.requestCode(request.email());
    }

    @PostMapping("/verify")
    @Operation(summary = "재설정 코드 확인", description = """
            ### 입력·다음 단계
            이메일과 수신한 인증 코드를 보냅니다. 성공하면 `resetToken`을 받아 새 비밀번호 재설정 요청에 복사합니다.

            코드는 성공 즉시 사용할 수 없게 됩니다. resetToken은 10분 유효한 일회용 값이며 회원가입의 verificationToken과 바꿔 사용할 수 없습니다.
            """)
    @ApiResponse(responseCode = "200", description = "재설정 증명 발급", content = @Content(schema = @Schema(implementation = PasswordResetProofResponse.class)))
    @ApiResponse(responseCode = "429", description = "코드 오입력 5회", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PasswordResetProofResponse verify(@Valid @RequestBody PasswordResetVerifyRequest request) {
        var proof = service.verify(request.email(), request.code());
        return new PasswordResetProofResponse(proof.resetToken(), proof.expiresIn());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "새 비밀번호로 재설정", description = """
            ### 입력
            이메일·코드 확인 응답의 `resetToken`·새 비밀번호를 보냅니다. 로그인 토큰은 필요하지 않습니다.

            ### 완료 후 화면 처리

            - 성공하면 모든 기기에서 다시 로그인해야 합니다.
            - 인증 증명은 한 번만 사용합니다. 처리 실패 시 이메일 인증부터 다시 진행합니다.
            - 응답을 받지 못했다면 새 비밀번호로 로그인해 변경 결과를 확인합니다.
            """)
    @ApiResponse(responseCode = "204", description = "비밀번호 재설정 완료. 자동 로그인하지 않음")
    public void reset(@Valid @RequestBody PasswordResetRequest request) {
        service.reset(request.email(), request.resetToken(), request.newPassword());
    }
}
