
package com.swu.atch.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 이메일 인증코드 확인 DTO
public record EmailCodeVerifyRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        @Pattern(regexp = "\\d{6}")
        String code
) {
}
