package com.swu.atch.ai.exception;

// Claude 외부 API 실패를 내부 비즈니스 로직과 구분
public class ClaudeApiException extends RuntimeException {

    public enum Reason {
        AUTHENTICATION,
        BILLING,
        BAD_REQUEST,
        RATE_LIMIT,
        UNAVAILABLE,
        INVALID_RESPONSE,
        UNKNOWN
    }

    private final Reason reason;
    private final int upstreamStatus;

    public ClaudeApiException(
            Reason reason,
            int upstreamStatus,
            Throwable cause
    ) {
        super("Claude API 호출 실패: " + reason, cause);
        this.reason = reason;
        this.upstreamStatus = upstreamStatus;
    }

    public Reason getReason() {
        return reason;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }
}
