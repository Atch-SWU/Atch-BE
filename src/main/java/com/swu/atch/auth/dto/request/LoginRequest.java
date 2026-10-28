
package com.swu.atch.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "이메일·비밀번호 로그인 요청")
public record LoginRequest(
        @Schema(description = "사용자가 입력한 이메일 주소입니다. 이메일 인증·회원가입·로그인에 같은 주소를 사용합니다.",
                example = "user@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 320)
        String email,
        @Schema(description = "가입한 비밀번호를 입력합니다.",
                example = "Atch123!",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String password,
        @Schema(description = "기기 식별자(필수, 최대 128자). 프론트가 기기를 구분하는 값을 정해 같은 기기에서 유지합니다. 사용자가 직접 입력할 값은 아닙니다.",
                example = "550e8400-e29b-41d4-a716-446655440000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 128)
        String deviceId
) {
}
