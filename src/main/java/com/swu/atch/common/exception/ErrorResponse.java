package com.swu.atch.common.exception;

import java.time.Instant;
import java.util.List;

// 공통 에러 응답 DTO
// * code: 서비스 내부 에러 코드
// * message: 사용자에게 전달할 오류 설명
// * path: 요청한 API 경로
// * timestamp: 오류 발생 시각 (UTC)
// * errors: 입력값 검증에 실패한 필드 목록
public record ErrorResponse(
        String code,
        String message,
        String path,
        Instant timestamp,
        List<FieldErrorDetail> errors
) {

    // 일반적인 비즈니스 오류 응답 생성
    public static ErrorResponse of(
            ErrorCode errorCode,
            String path
    ) {
        return of(
                errorCode.name(),
                errorCode.getMessage(),
                path,
                List.of()
        );
    }

    // 입력값 검증 오류 등 상세 정보가 필요한 경우 사용
    public static ErrorResponse of(
            String code,
            String message,
            String path,
            List<FieldErrorDetail> errors
    ) {
        return new ErrorResponse(
                code,
                message,
                path,
                Instant.now(),
                List.copyOf(errors)
        );
    }

    // 검증에 실패한 필드와 사유
    public record FieldErrorDetail(
            String field,
            String reason
    ) {
    }
}
