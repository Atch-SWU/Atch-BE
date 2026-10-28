package com.swu.atch.auth.dto.request;

import com.swu.atch.auth.dto.AccountInputRules;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PasswordResetVerifyRequest(
        @Schema(description = "LOCAL 계정 이메일", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 320) String email,
        @Schema(description = "이메일로 받은 6자리 코드", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {
    @Override public String toString() { return "PasswordResetVerifyRequest[REDACTED]"; }
}
