package com.swu.atch.ai.client;

// Claude 응답 텍스트와 토큰 사용량
public record ClaudeResult(
        String text,
        String model,
        long inputTokens,
        long outputTokens
) {
}
