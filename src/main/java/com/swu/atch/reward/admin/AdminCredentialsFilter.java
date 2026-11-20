package com.swu.atch.reward.admin;

import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.common.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@RequiredArgsConstructor
public class AdminCredentialsFilter extends OncePerRequestFilter {
    private final AdminAccess access;
    private final JsonMapper json;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal principal) {
            boolean valid;
            try {
                valid = access.isCurrent(principal);
            } catch (DataAccessException ex) {
                error(response, request, ErrorCode.AUTH_SERVICE_UNAVAILABLE);
                return;
            }
            if (!valid) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                if (request.getRequestURI().startsWith("/api/")) error(response, request, ErrorCode.UNAUTHORIZED);
                else response.sendRedirect("/admin/login?expired=1");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void error(HttpServletResponse response, HttpServletRequest request, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getWriter(), ErrorResponse.of(code, request.getRequestURI()));
    }
}
