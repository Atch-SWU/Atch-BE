
package com.swu.atch.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "이메일 인증 완료 후 회원가입 요청")
public record SignUpRequest(
        @Schema(description = "사용자가 입력한 이메일 주소입니다. 이메일 인증·회원가입·로그인에 같은 주소를 사용합니다.",
                example = "user@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 320)
        String email,
        @Schema(description = "비밀번호. 8~16자이며 영문 대문자·소문자·지정 특수문자(!@#$%^&*)를 각각 포함합니다. 허용 문자는 영문·숫자·지정 특수문자입니다.",
                example = "Atch123!",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*])[A-Za-z0-9!@#$%^&*]{8,16}$",
                message = "비밀번호는 8~16자이며 영문 대문자, 소문자, 지정 특수문자를 각각 포함해야 합니다."
        )
        String password,
        @Schema(description = "닉네임. 한글 또는 영문 2~8자를 입력합니다. 중복 닉네임을 허용합니다.",
                example = "엣취사용자",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Pattern(
                regexp = "^[가-힣a-zA-Z]{2,8}$",
                message = "닉네임은 한글 또는 영문 2~8자여야 합니다."
        )
        String nickname,
        @Schema(description = "회원가입용 이메일 인증 증명. 이메일 코드 확인 응답에서 받은 값을 그대로 보냅니다. 비밀번호 재설정용 resetToken과 다릅니다.",
                example = "verificationTokenFromVerifyResponse",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String verificationToken,
        @Schema(description = "기기 식별자(필수, 최대 128자). 프론트가 기기를 구분하는 값을 정해 같은 기기에서 유지합니다. 사용자가 직접 입력할 값은 아닙니다.",
                example = "550e8400-e29b-41d4-a716-446655440000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 128)
        String deviceId
) {
}
