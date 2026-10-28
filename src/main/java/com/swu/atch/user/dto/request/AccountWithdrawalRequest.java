package com.swu.atch.user.dto.request;

import com.swu.atch.auth.dto.AccountInputRules;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AccountWithdrawalRequest(
        @Schema(description = "현재 비밀번호", example = "Atch123!", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 256) String currentPassword
) {
    @Override public String toString() { return "AccountWithdrawalRequest[REDACTED]"; }
}
