package com.swu.atch.reward.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AdminItemBatchResponse(
        @Schema(description = "입력 순서대로 반환하는 등록 결과. 동일 코드·동일 내용의 재전송에는 기존 아이템을 반환합니다.")
        List<AdminCatalogItemResponse> items
) { }
