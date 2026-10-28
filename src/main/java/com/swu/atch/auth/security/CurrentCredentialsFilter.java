package com.swu.atch.auth.security;

import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.common.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

// JWT 서명뿐 아니라 현재 LOCAL 계정의 존재·비밀번호 상태도 확인
@RequiredArgsConstructor
public class CurrentCredentialsFilter extends OncePerRequestFilter {
    private final AccountCredentials credentials;
    private final SecurityErrorHandler errorHandler;
    private final JsonMapper jsonMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/v1/auth/")
                || request.getRequestURI().startsWith("/api/v1/users/me")) {
            response.setHeader("Cache-Control", "no-store");
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            boolean valid;
            try {
                long userId = Long.parseLong(jwt.getToken().getSubject());
                valid = userId > 0 && credentials.isCurrent(userId,
                        jwt.getToken().getClaimAsString("credential_stamp"));
            } catch (NumberFormatException ex) {
                valid = false;
            } catch (DataAccessException ex) {
                SecurityContextHolder.clearContext();
                response.setStatus(503);
                response.setContentType("application/json;charset=UTF-8");
                jsonMapper.writeValue(response.getWriter(), ErrorResponse.of(
                        ErrorCode.AUTH_SERVICE_UNAVAILABLE, request.getRequestURI()));
                return;
            }
            if (!valid) {
                SecurityContextHolder.clearContext();
                errorHandler.commence(request, response, new BadCredentialsException("Invalid account state"));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
