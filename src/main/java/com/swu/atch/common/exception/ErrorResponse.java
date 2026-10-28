package com.swu.atch.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

// 공통 에러 응답 DTO
// * code: 서비스 내부 에러 코드
// * message: 사용자에게 전달할 오류 설명
// * path: 요청한 API 경로
// * timestamp: 오류 발생 시각 (UTC)
// * errors: 입력값 검증에 실패한 필드 목록
public record ErrorResponse(
        @Schema(description = "오류 코드. HTTP 상태와 함께 확인하고 코드별로 화면 처리를 분기합니다.")
        String code,
        @Schema(description = "오류에 대한 한국어 설명입니다.")
        String message,
        @Schema(description = "오류가 발생한 요청 경로입니다.")
        String path,
        @Schema(description = "오류 발생 시각(UTC)입니다.")
        Instant timestamp,
        @Schema(description = "입력 검증에 실패한 필드 목록. 필드 오류가 없으면 빈 배열입니다.")
        List<FieldErrorDetail> errors) {

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
            @Schema(description = "입력 검증에 실패한 필드명. 해당 입력란을 찾을 때 사용합니다.")
            String field,
            @Schema(description = "해당 필드의 오류 이유. 입력란의 안내 문구로 표시할 수 있습니다.")
            String reason) {
    }
}
