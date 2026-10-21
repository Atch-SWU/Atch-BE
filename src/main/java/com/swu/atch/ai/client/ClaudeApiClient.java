package com.swu.atch.ai.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.swu.atch.ai.exception.ClaudeApiException;
import com.swu.atch.ai.exception.ClaudeApiException.Reason;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

// Claude Messages API 공통 호출 클라이언트
@Component
@ConditionalOnProperty(
        name = "app.ai.claude.enabled",
        havingValue = "true"
)
public class ClaudeApiClient {

    private static final int MAX_PROMPT_LENGTH = 8000;

    private final AnthropicClient client;
    private final String model;
    private final long defaultMaxTokens;
    private final long maxAllowedTokens;

    public ClaudeApiClient(
            AnthropicClient client,
            @Value("${app.ai.claude.model}") String model,
            @Value("${app.ai.claude.max-output-tokens}") long defaultMaxTokens,
            @Value("${app.ai.claude.max-allowed-output-tokens}") long maxAllowedTokens
    ) {
        this.client = client;
        this.model = model;
        this.defaultMaxTokens = defaultMaxTokens;
        this.maxAllowedTokens = maxAllowedTokens;
    }

    // 기본 출력 토큰 제한으로 Claude 호출
    public ClaudeResult generate(String prompt) {
        return generate(prompt, defaultMaxTokens);
    }

    // 호출 목적에 따라 출력 토큰 상한 지정
    public ClaudeResult generate(String prompt, long maxTokens) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException(
                    "Claude 요청 프롬프트는 비어 있을 수 없습니다."
            );
        }

        if (prompt.length() > MAX_PROMPT_LENGTH) {
            throw new IllegalArgumentException(
                    "Claude 요청 프롬프트 길이 제한을 초과했습니다."
            );
        }

        if (maxTokens < 1 || maxTokens > maxAllowedTokens) {
            throw new IllegalArgumentException(
                    "허용된 최대 출력 토큰 범위를 벗어났습니다."
            );
        }

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .addUserMessage(prompt)
                .build();

        try {
            Message response = client.messages().create(params);

            // 텍스트 블록만 추출
            String text = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(block -> block.text())
                    .collect(Collectors.joining("\n"));

            if (text.isBlank()) {
                throw new ClaudeApiException(
                        Reason.INVALID_RESPONSE, 0, null
                );
            }

            return new ClaudeResult(
                    text,
                    model,
                    response.usage().inputTokens(),
                    response.usage().outputTokens()
            );

        } catch (AnthropicServiceException ex) {
            int status = ex.statusCode();

            Reason reason = switch (status) {
                case 401, 403 -> Reason.AUTHENTICATION;
                case 402 -> Reason.BILLING;
                case 400, 404, 422 -> Reason.BAD_REQUEST;
                case 429 -> Reason.RATE_LIMIT;
                default -> status >= 500
                        ? Reason.UNAVAILABLE
                        : Reason.UNKNOWN;
            };

            throw new ClaudeApiException(reason, status, ex);

        } catch (AnthropicIoException ex) {
            throw new ClaudeApiException(
                    Reason.UNAVAILABLE, 0, ex
            );

        } catch (AnthropicException ex) {
            throw new ClaudeApiException(
                    Reason.UNKNOWN, 0, ex
            );
        }
    }
}
