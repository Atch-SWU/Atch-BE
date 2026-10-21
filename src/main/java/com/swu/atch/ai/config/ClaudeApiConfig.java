package com.swu.atch.ai.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// Claude API 클라이언트 설정
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "app.ai.claude.enabled",
        havingValue = "true"
)
public class ClaudeApiConfig {

    @Bean
    public AnthropicClient anthropicClient(
            @Value("${app.ai.claude.api-key}") String apiKey,
            @Value("${app.ai.claude.timeout}") Duration timeout
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Claude API가 활성화됐지만 API 키가 없습니다."
            );
        }

        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(0)
                .build();
    }
}
