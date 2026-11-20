package com.swu.atch.reward.admin;

// 파일 검증을 마친 입력만 업로드·저장 단계로 전달한다.
public record PreparedCharacterItem(AdminItemBatchRequest.Item input, byte[] image,
        String sha256, String contentType, String extension) { }
