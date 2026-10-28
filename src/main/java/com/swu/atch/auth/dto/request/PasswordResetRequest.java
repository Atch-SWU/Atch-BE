package com.swu.atch.auth.dto.request;

import com.swu.atch.auth.dto.AccountInputRules;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PasswordResetRequest(
        @Schema(description = "사용자가 입력한 이메일 주소입니다. 이메일 인증·회원가입·로그인에 같은 주소를 사용합니다.", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 320) String email,
        @Schema(description = "비밀번호 재설정용 인증 증명. 재설정 코드 확인 응답에서 받은 값을 그대로 보냅니다. 10분 유효하며 한 번만 사용할 수 있습니다.", example = "resetTokenFromVerifyResponse", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 128) String resetToken,
        @Schema(description = "새 비밀번호. 8~16자이며 영문 대문자·소문자·특수문자(!@#$%^&*)를 각각 포함해야 합니다.", example = "NewAtch123!", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Pattern(regexp = AccountInputRules.PASSWORD) String newPassword
) {
    @Override public String toString() { return "PasswordResetRequest[REDACTED]"; }
}
