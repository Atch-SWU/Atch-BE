package com.swu.atch.reward.admin;

import java.util.List;

public record AdminItemPageResponse(List<AdminCatalogItemResponse> items, int page, int size,
        long totalElements, boolean hasNext) { }
