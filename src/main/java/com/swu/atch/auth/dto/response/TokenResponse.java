
package com.swu.atch.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "로그인 성공 시 발급되는 Access·Refresh Token")
public record TokenResponse(
        @Schema(description = "Authorization 헤더에 사용할 인증 방식", example = "Bearer")
        String tokenType,
        @Schema(description = "API 인증용 RS256 JWT. Authorization: Bearer {accessToken} 형식으로 전달")
        String accessToken,
        @Schema(description = "Access Token 유효기간(초)", example = "900")
        long accessTokenExpiresIn,
        @Schema(description = "갱신·로그아웃 요청 본문에 사용할 리프레시 토큰입니다. 안전하게 보관하고 갱신 성공 시 새 값으로 교체합니다.")
        String refreshToken,
        @Schema(description = "Refresh Session의 절대 만료 시각(UTC)",
                example = "2026-11-04T07:20:00Z")
        Instant refreshTokenExpiresAt
) {
}
