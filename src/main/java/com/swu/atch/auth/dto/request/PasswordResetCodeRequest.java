package com.swu.atch.auth.dto.request;

import com.swu.atch.auth.dto.AccountInputRules;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PasswordResetCodeRequest(
        @Schema(description = "사용자가 입력한 이메일 주소입니다. 이메일 인증·회원가입·로그인에 같은 주소를 사용합니다.", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 320) String email
) {
    @Override public String toString() { return "PasswordResetCodeRequest[REDACTED]"; }
}
