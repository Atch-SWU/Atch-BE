package com.swu.atch.auth.dto.request;

import com.swu.atch.auth.dto.AccountInputRules;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PasswordResetCodeRequest(
        @Schema(description = "LOCAL 계정 이메일", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 320) String email
) {
    @Override public String toString() { return "PasswordResetCodeRequest[REDACTED]"; }
}
