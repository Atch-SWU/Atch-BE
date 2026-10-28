package com.swu.atch.user.dto.request;

import com.swu.atch.auth.dto.AccountInputRules;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PasswordChangeRequest(
        @Schema(description = "현재 비밀번호", example = "Atch123!", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 256) String currentPassword,
        @Schema(description = "8~16자, 영문 대문자·소문자·특수문자(!@#$%^&*) 각각 1개 이상", example = "NewAtch123!", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Pattern(regexp = AccountInputRules.PASSWORD) String newPassword
) {
    @Override public String toString() { return "PasswordChangeRequest[REDACTED]"; }
}
