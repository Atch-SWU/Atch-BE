
package com.swu.atch.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "이메일·비밀번호 로그인 요청")
public record LoginRequest(

        @Schema(description = "가입한 LOCAL 계정 이메일",
                example = "user@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 320)
        String email,

        @Schema(description = "회원가입 시 설정한 비밀번호",
                example = "Atch123!",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String password,

        @Schema(description = "기기별 Refresh Session 구분에 사용할 식별자",
                example = "550e8400-e29b-41d4-a716-446655440000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 128)
        String deviceId
) {
}
