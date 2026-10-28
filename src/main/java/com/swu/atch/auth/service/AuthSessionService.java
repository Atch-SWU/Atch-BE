package com.swu.atch.auth.service;

import com.swu.atch.auth.dto.response.TokenResponse;
import com.swu.atch.auth.jwt.JwtTokenService;
import com.swu.atch.auth.refresh.RefreshTokenService;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

// 발급·갱신·계정 변경은 동일한 User 잠금을 사용
@Service
public class AuthSessionService {
    private final RefreshTokenService refreshTokens;
    private final JwtTokenService jwtTokens;
    private final UserRepository users;
    private final AccountCredentials credentials;
    private final Duration accessTokenTtl;

    public AuthSessionService(RefreshTokenService refreshTokens, JwtTokenService jwtTokens,
                              UserRepository users, AccountCredentials credentials,
                              @Value("${app.jwt.access-token-ttl}") Duration accessTokenTtl) {
        this.refreshTokens = refreshTokens;
        this.jwtTokens = jwtTokens;
        this.users = users;
        this.credentials = credentials;
        this.accessTokenTtl = accessTokenTtl;
    }

    @Transactional
    public TokenResponse issue(Long userId, String deviceId, String authenticatedStamp) {
        users.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        var account = credentials.localAccount(userId);
        if (authenticatedStamp != null) credentials.requireCurrent(account, authenticatedStamp);
        String stamp = credentials.stamp(account);
        try {
            var refresh = refreshTokens.create(userId, deviceId, stamp);
            cleanupOnRollback(refresh.token());
            return response(userId, stamp, refresh.token(), refresh.expiresAt());
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        try {
            Long owner = refreshTokens.ownerOf(refreshToken);
            if (users.findByIdForUpdate(owner).isEmpty()) {
                refreshTokens.revoke(refreshToken);
                throw new BusinessException(ErrorCode.REFRESH_SESSION_INVALID);
            }
            var rotated = refreshTokens.rotate(refreshToken);
            cleanupOnRollback(rotated.token());
            if (!owner.equals(rotated.userId()) || !credentials.isCurrent(owner, rotated.credentialStamp())) {
                refreshTokens.revoke(rotated.token());
                throw new BusinessException(ErrorCode.REFRESH_SESSION_INVALID);
            }
            return response(owner, rotated.credentialStamp(), rotated.token(), rotated.expiresAt());
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
    }

    public void logout(String refreshToken) {
        try {
            refreshTokens.revoke(refreshToken);
        } catch (BusinessException ex) {
            // 이미 폐기된 토큰의 로그아웃도 성공
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
    }

    private TokenResponse response(Long userId, String stamp, String refreshToken, java.time.Instant expiresAt) {
        return new TokenResponse("Bearer", jwtTokens.issueAccessToken(userId, stamp),
                accessTokenTtl.toSeconds(), refreshToken, expiresAt);
    }

    // DB 커밋 실패로 응답을 반환하지 못하면 Redis에 남은 새 토큰도 폐기
    private void cleanupOnRollback(String token) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { refreshTokens.revoke(token); }
                    catch (RuntimeException ignored) { /* TTL 및 credential stamp로 사용 제한 */ }
                }
            }
        });
    }
}
