package com.swu.atch.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record NicknameResponse(
        @Schema(description = "현재 저장된 닉네임입니다.", example = "엣취") String nickname) { }
