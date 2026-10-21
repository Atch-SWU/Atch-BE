
package com.swu.atch.auth.service;

import com.swu.atch.auth.dto.response.TokenResponse;
import com.swu.atch.auth.jwt.JwtTokenService;
import com.swu.atch.auth.refresh.RefreshTokenService;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Refresh Token 기반 세션 갱신 및 로그아웃
@Slf4j
@Service
public class AuthSessionService {

    private final RefreshTokenService refreshTokenService;
    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;
    private final Duration accessTokenTtl;

    public AuthSessionService(
            RefreshTokenService refreshTokenService,
            JwtTokenService jwtTokenService,
            UserRepository userRepository,
            @Value("${app.jwt.access-token-ttl}") Duration accessTokenTtl
    ) {
        this.refreshTokenService = refreshTokenService;
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
        this.accessTokenTtl = accessTokenTtl;
    }

    // 기존 Refresh Token 소비 후 새 Access·Refresh Token 발급
    public TokenResponse refresh(String refreshToken) {
        RefreshTokenService.RotatedRefreshToken rotated;

        try {
            rotated = refreshTokenService.rotate(refreshToken);
        } catch (RedisConnectionFailureException ex) {
            log.error("Refresh Session 갱신 중 Redis 연결 실패", ex);
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }

        try {
            // 삭제된 사용자의 인증 세션으로 새 JWT 발급 방지
            if (!userRepository.existsById(rotated.userId())) {
                throw new BusinessException(ErrorCode.REFRESH_SESSION_INVALID);
            }

            String accessToken = jwtTokenService.issueAccessToken(
                    rotated.userId()
            );

            return new TokenResponse(
                    "Bearer",
                    accessToken,
                    accessTokenTtl.toSeconds(),
                    rotated.token(),
                    rotated.expiresAt()
            );

        } catch (RuntimeException ex) {
            // 토큰 응답 구성 실패 시 새 Refresh Token도 폐기
            try {
                refreshTokenService.revoke(rotated.token());
            } catch (RuntimeException cleanupEx) {
                log.error(
                        "Refresh Session 정리 실패, userId={}",
                        rotated.userId(),
                        cleanupEx
                );
            }
            throw ex;
        }
    }

    // 현재 Refresh Session 폐기
    public void logout(String refreshToken) {
        try {
            refreshTokenService.revoke(refreshToken);
        } catch (BusinessException ex) {
            // 이미 만료되었거나 잘못된 토큰도 로그아웃 완료로 처리
            return;
        } catch (RedisConnectionFailureException ex) {
            log.error("로그아웃 중 Redis 연결 실패", ex);
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
    }
}
