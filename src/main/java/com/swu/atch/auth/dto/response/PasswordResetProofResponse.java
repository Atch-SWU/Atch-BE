package com.swu.atch.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record PasswordResetProofResponse(
        @Schema(description = "비밀번호 재설정 전용 일회용 증명", example = "resetTokenFromVerifyResponse") String resetToken,
        @Schema(description = "증명 유효기간(초)", example = "600") long expiresIn
) {
    @Override public String toString() { return "PasswordResetProofResponse[REDACTED]"; }
}
