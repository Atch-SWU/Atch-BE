package com.swu.atch.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh Token 기반 인증 세션 요청")
public record RefreshTokenRequest(
        @Schema(
                description = "로그인·갱신 응답에서 받은 리프레시 토큰. 요청 본문으로 전달합니다. 갱신 성공 시 새 토큰으로 교체하고 같은 이전 토큰을 동시에 사용하지 않습니다.",
                example = "550e8400-e29b-41d4-a716-446655440000.randomSecret",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        String refreshToken
) {
}
