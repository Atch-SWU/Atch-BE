package com.swu.atch.user.dto.request;

import com.swu.atch.auth.dto.AccountInputRules;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record NicknameUpdateRequest(
        @Schema(description = "한글 또는 영문 2~8자. 중복 허용", example = "엣취", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Pattern(regexp = AccountInputRules.NICKNAME) String nickname
) {
    @Override public String toString() { return "NicknameUpdateRequest[REDACTED]"; }
}
