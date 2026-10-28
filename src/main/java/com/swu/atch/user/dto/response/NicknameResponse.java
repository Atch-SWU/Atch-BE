package com.swu.atch.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record NicknameResponse(@Schema(description = "변경된 닉네임", example = "엣취") String nickname) { }
