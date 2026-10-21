package com.swu.atch.common.exception;

// 서비스 예외
public class BusinessException extends RuntimeException { // RuntimeException을 상속하므로 별도의 throws 선언 없이 사용 가능

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
