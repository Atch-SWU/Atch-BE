package com.swu.atch.auth.dto.request;

import com.swu.atch.auth.dto.AccountInputRules;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PasswordResetRequest(
        @Schema(description = "LOCAL 계정 이메일", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 320) String email,
        @Schema(description = "재설정 코드 확인 응답의 일회용 증명. 가입 verificationToken과 다름", example = "resetTokenFromVerifyResponse", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 128) String resetToken,
        @Schema(description = "8~16자, 영문 대문자·소문자·특수문자(!@#$%^&*) 각각 1개 이상", example = "NewAtch123!", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Pattern(regexp = AccountInputRules.PASSWORD) String newPassword
) {
    @Override public String toString() { return "PasswordResetRequest[REDACTED]"; }
}
