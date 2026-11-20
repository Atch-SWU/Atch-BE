package com.swu.atch.reward.admin;

import com.swu.atch.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Order(0)
@RestControllerAdvice(assignableTypes = AdminItemController.class)
public class AdminItemExceptionHandler {
    @ExceptionHandler(AdminItemException.class)
    ResponseEntity<ErrorResponse> handle(AdminItemException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(ErrorResponse.of(exception.code(),
                exception.getMessage(), request.getRequestURI(), List.of()));
    }
}
