package com.swu.atch.ai.client;

import com.swu.atch.ai.dto.response.TimePredictionResult.ClaudeStatus;

// 제공자 응답 본문·키·프롬프트를 오류 메시지에 포함하지 않는다.
public class AiProviderException extends RuntimeException {
    private final ClaudeStatus status;

    public AiProviderException(ClaudeStatus status) {
        super("AI provider: " + status);
        this.status = status;
    }

    public static ClaudeStatus ioStatus(Throwable cause) {
        for (int i = 0; cause != null && i < 32; i++, cause = cause.getCause()) {
            if (cause instanceof java.io.InterruptedIOException
                    || cause instanceof java.net.http.HttpTimeoutException)
                return ClaudeStatus.TIMEOUT;
        }
        return ClaudeStatus.UNAVAILABLE;
    }

    public ClaudeStatus status() {
        return status;
    }
}
