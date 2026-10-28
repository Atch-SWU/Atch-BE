
package com.swu.atch.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 이메일 인증코드 발송 DTO
public record EmailCodeRequest(
        @Schema(description = "사용자가 입력한 이메일 주소입니다. 이메일 인증·회원가입·로그인에 같은 주소를 사용합니다.")
        @NotBlank
        @Email
        @Size(max = 320)
        String email
) {
}
