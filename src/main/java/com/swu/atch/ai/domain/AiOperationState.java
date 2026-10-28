package com.swu.atch.ai.domain;

// 기존 AI 값만으로는 Claude 성공 여부를 복원하지 않는다.
public enum AiOperationState {
    IN_PROGRESS,
    SUCCEEDED,
    RETRYABLE_FAILURE,
    LEGACY_UNKNOWN
}
