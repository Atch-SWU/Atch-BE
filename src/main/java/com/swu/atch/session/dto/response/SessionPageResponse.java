package com.swu.atch.session.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

// 본인 Todo의 집중 구간 페이지
@Schema(description = "본인 Todo의 집중 구간 페이지")
public record SessionPageResponse(
        Long todoId, List<SessionResponse> items, int page, int size,
        long totalElements, int totalPages, boolean hasNext
) {

}

