package com.swu.atch.ai.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.ai.claude.enabled=true")
@EnabledIfEnvironmentVariable(
        named = "RUN_CLAUDE_LIVE_TEST",
        matches = "true"
)
class ClaudeApiClientLiveTest {

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Test
    void generateText() {
        ClaudeResult result = claudeApiClient.generate(
                "Atch! 백엔드의 Claude API 연동 테스트입니다. " +
                        "한국어로 짧게 인사해주세요.",
                64
        );

        assertNotNull(result.text());
        assertFalse(result.text().isBlank());
        assertTrue(result.inputTokens() >= 0);
        assertTrue(result.outputTokens() > 0);

        // 전체 응답 대신 모델과 토큰 사용량만 출력
        System.out.println("Model: " + result.model());
        System.out.println("Input tokens: " + result.inputTokens());
        System.out.println("Output tokens: " + result.outputTokens());
    }
}
