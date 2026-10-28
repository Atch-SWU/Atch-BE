package com.swu.atch.auth.dto.request;

import com.swu.atch.auth.dto.AccountInputRules;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PasswordResetVerifyRequest(
        @Schema(description = "사용자가 입력한 이메일 주소입니다. 이메일 인증·회원가입·로그인에 같은 주소를 사용합니다.", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 320) String email,
        @Schema(description = "이메일로 받은 인증 코드입니다. 앞자리 0이 사라지지 않도록 문자열로 보냅니다.", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {
    @Override public String toString() { return "PasswordResetVerifyRequest[REDACTED]"; }
}
