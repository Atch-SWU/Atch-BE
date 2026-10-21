
package com.swu.atch.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 이메일 인증코드 발송 DTO
public record EmailCodeRequest(
        @NotBlank
        @Email
        @Size(max = 320)
        String email
) {
}
