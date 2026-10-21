package com.swu.atch.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh Token 기반 인증 세션 요청")
public record RefreshTokenRequest(

        @Schema(
                description = "로그인 또는 토큰 갱신 API에서 발급받은 Refresh Token. Access Token이나 이메일 인증용 verificationToken과 다름",
                example = "550e8400-e29b-41d4-a716-446655440000.randomSecret",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        String refreshToken
) {
}
