package com.swu.atch.user.dto.request;

import com.swu.atch.auth.dto.AccountInputRules;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PasswordChangeRequest(
        @Schema(description = "현재 비밀번호. 본인 확인을 위해 사용자가 다시 입력합니다.", example = "Atch123!", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 256) String currentPassword,
        @Schema(description = "새 비밀번호. 8~16자이며 영문 대문자·소문자·특수문자(!@#$%^&*)를 각각 포함해야 합니다.", example = "NewAtch123!", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Pattern(regexp = AccountInputRules.PASSWORD) String newPassword
) {
    @Override public String toString() { return "PasswordChangeRequest[REDACTED]"; }
}
