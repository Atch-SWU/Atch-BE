package com.swu.atch.common.exception;


import org.springframework.http.HttpStatus;

// 공통 에러 코드 정의
// 도메인별 상세 에러코드 추후 확장
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "요청한 작업이 현재 상태와 충돌합니다."),
    TODO_PAUSE_REQUIRED(HttpStatus.CONFLICT, "진행 중인 Todo를 먼저 일시정지해주세요."),
    TODO_REOPEN_REQUIRED(HttpStatus.CONFLICT, "완료된 Todo를 수정하려면 먼저 완료를 취소해주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    // Refresh Token이 만료됐거나, 세션이 삭제됐거나, 이전 토큰 재사용이 감지된 경우
    REFRESH_SESSION_INVALID(HttpStatus.UNAUTHORIZED, "인증 세션이 유효하지 않습니다. 다시 로그인해주세요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    CURRENT_PASSWORD_INVALID(HttpStatus.FORBIDDEN, "현재 비밀번호가 올바르지 않습니다."),
    PASSWORD_RESET_PROOF_INVALID(HttpStatus.BAD_REQUEST, "재설정 인증이 유효하지 않습니다. 이메일 인증부터 다시 진행해주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    EMAIL_RESEND_TOO_SOON(
            HttpStatus.TOO_MANY_REQUESTS,
            "인증코드는 60초 후 다시 요청할 수 있습니다."
    ),
    EMAIL_CODE_INVALID(
            HttpStatus.BAD_REQUEST,
            "인증코드가 올바르지 않거나 만료되었습니다."
    ),
    EMAIL_CODE_ATTEMPTS_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS,
            "인증 시도 횟수를 초과했습니다. 인증코드를 다시 요청해주세요."
    ),
    EMAIL_SEND_FAILED(
            HttpStatus.BAD_GATEWAY,
            "인증메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요."
    ),
    EMAIL_VERIFICATION_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "이메일 인증이 유효하지 않습니다. 다시 인증해주세요."
    ),
    EMAIL_ALREADY_REGISTERED(
            HttpStatus.CONFLICT,
            "이미 가입된 이메일입니다."
    ),

    SIGNUP_FAILED_REVERIFY(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "회원가입 정보를 저장하지 못했습니다. 가입 여부를 확인한 뒤 이메일 인증부터 다시 시도해주세요."
    ),
    SIGNUP_COMPLETED_LOGIN_FAILED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "회원가입은 완료되었지만 자동 로그인에 실패했습니다. 로그인 화면에서 다시 로그인해주세요."
    ),
    AUTH_SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "인증 서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요."
    ),
    ;

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
