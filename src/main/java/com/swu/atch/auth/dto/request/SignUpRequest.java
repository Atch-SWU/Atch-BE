
package com.swu.atch.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "이메일 인증 완료 후 회원가입 요청")
public record SignUpRequest(

        @Schema(description = "이메일 인증을 완료한 주소",
                example = "user@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 320)
        String email,

        @Schema(description = "8~16자, 영문 대문자·소문자·지정 특수문자(!@#$%^&*) 각각 1개 이상 포함",
                example = "Atch123!",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*])[A-Za-z0-9!@#$%^&*]{8,16}$",
                message = "비밀번호는 8~16자이며 영문 대문자, 소문자, 지정 특수문자를 각각 포함해야 합니다."
        )
        String password,

        @Schema(description = "한글 또는 영문 2~8자",
                example = "엣취사용자",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Pattern(
                regexp = "^[가-힣a-zA-Z]{2,8}$",
                message = "닉네임은 한글 또는 영문 2~8자여야 합니다."
        )
        String nickname,

        @Schema(description = "이메일 인증 확인 API가 반환한 verificationToken. 6자리 코드가 아님",
                example = "verificationTokenFromVerifyResponse",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String verificationToken,

        @Schema(description = "앱 설치별 식별자. 최초 생성 후 기기에 보관하며 Refresh Session 구분에 사용",
                example = "550e8400-e29b-41d4-a716-446655440000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 128)
        String deviceId
) {
}
