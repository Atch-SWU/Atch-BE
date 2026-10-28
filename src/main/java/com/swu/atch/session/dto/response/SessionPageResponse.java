package com.swu.atch.session.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

// 본인 Todo의 집중 구간 페이지
@Schema(description = "본인 Todo의 집중 구간 페이지")
public record SessionPageResponse(
        @Schema(description = "서버가 발급한 투두 ID. 투두 생성·조회 응답에서 받은 값입니다.")
        Long todoId,
        @Schema(description = "조회한 항목 목록입니다.")
        List<SessionResponse> items,
        @Schema(description = "현재 페이지 번호. 0이 첫 페이지입니다.")
        int page,
        @Schema(description = "요청에 적용된 페이지당 최대 항목 수입니다.")
        int size,
        @Schema(description = "조회 조건에 맞는 전체 항목 수입니다.")
        long totalElements,
        @Schema(description = "전체 페이지 수입니다.")
        int totalPages,
        @Schema(description = "다음 페이지가 있는지 여부입니다. true이면 page를 1 증가시켜 다음 목록을 조회할 수 있습니다.")
        boolean hasNext
) {

}

