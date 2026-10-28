package com.swu.atch.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

// 전역 예외 처리기
// Controller에서 발생한 예외를 공통 JSON 응답으로 변환
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 서비스 비즈니스 예외 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = ex.getErrorCode();

        var headers = new HttpHeaders();
        if (errorCode == ErrorCode.AI_RATE_LIMITED) headers.set(HttpHeaders.RETRY_AFTER, "60");
        return ResponseEntity
                .status(errorCode.getStatus()).headers(headers)
                .body(ErrorResponse.of(
                        errorCode,
                        request.getRequestURI()
                ));
    }

    // @Valid를 통한 요청 DTO 검증 실패 처리
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<ErrorResponse.FieldErrorDetail> errors =
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error ->
                                new ErrorResponse.FieldErrorDetail(
                                        error.getField(),
                                        error.getDefaultMessage()
                                ))
                        .toList();

        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;

        ErrorResponse response = ErrorResponse.of(
                errorCode.name(),
                errorCode.getMessage(),
                getRequestPath(request),
                errors
        );

        return new ResponseEntity<>(
                response,
                headers,
                status
        );
    }


    // Spring MVC에서 발생한 기본 HTTP 오류를 공통 형식으로 변환
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        if (body instanceof ErrorResponse errorResponse) {
            return new ResponseEntity<>(
                    errorResponse,
                    headers,
                    status
            );
        }

        HttpStatus httpStatus = HttpStatus.resolve(status.value());

        String code = httpStatus != null
                ? httpStatus.name()
                : "HTTP_" + status.value();

        String message = switch (status.value()) {
            case 400 -> "요청 형식이 올바르지 않습니다.";
            case 404 -> "요청한 경로를 찾을 수 없습니다.";
            case 405 -> "지원하지 않는 HTTP 메서드입니다.";
            case 415 -> "지원하지 않는 요청 데이터 형식입니다.";
            default -> status.is5xxServerError()
                    ? "서버 내부 오류가 발생했습니다."
                    : "요청을 처리할 수 없습니다.";
        };

        ErrorResponse response = ErrorResponse.of(
                code,
                message,
                getRequestPath(request),
                List.of()
        );

        return new ResponseEntity<>(
                response,
                headers,
                status
        );
    }

    // 별도로 처리하지 않은 예상치 못한 서버 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error(
                "Unhandled exception: {}",
                request.getRequestURI(),
                ex
        );

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(
                        errorCode,
                        request.getRequestURI()
                ));
    }

    // Spring WebRequest에서 요청 경로 추출
    private String getRequestPath(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }

        return request.getDescription(false);
    }
}
