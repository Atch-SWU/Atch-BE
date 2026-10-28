package com.swu.atch.session.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

// Session 조회 페이지. page는 0부터 시작하고 size 기본값은 20
@Schema(description = "Session 조회 페이지. page는 0부터 시작하고 size 기본값은 20")
public record SessionPageQuery(
        @Min(0) @Schema(description = "0부터 시작하는 페이지 번호", defaultValue = "0") Integer page,
        @Min(1) @Max(100) @Schema(description = "페이지 크기. 최대 100", defaultValue = "20") Integer size
) {
    public SessionPageQuery {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}

