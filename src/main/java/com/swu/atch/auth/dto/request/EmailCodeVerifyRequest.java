
package com.swu.atch.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 이메일 인증코드 확인 DTO
public record EmailCodeVerifyRequest(
        @Schema(description = "사용자가 입력한 이메일 주소입니다. 이메일 인증·회원가입·로그인에 같은 주소를 사용합니다.")
        @NotBlank
        @Email
        String email,
        @Schema(description = "이메일로 받은 인증 코드입니다. 앞자리 0이 사라지지 않도록 문자열로 보냅니다.")
        @NotBlank
        @Pattern(regexp = "\\d{6}")
        String code
) {
}
