
package com.swu.atch.auth.service;

import com.swu.atch.auth.refresh.RefreshTokenService;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.repository.AuthAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.junit.jupiter.api.Assertions.*;

class AuthSessionServiceTest extends IsolatedAuthIntegrationTest {

    @Autowired
    private AuthSessionService authSessionService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired private AuthAccountRepository accounts;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AccountCredentials credentials;

    @Test
    void refreshIssuesTokensAndRejectsReuse() {
        User user = userRepository.save(new User("테스트사용자"));
        var account = accounts.save(AuthAccount.local(user, "refresh-test@example.com", encoder.encode("Atch123!")));
        var issued = refreshTokenService.create(user.getId(), "test-device", credentials.stamp(account));
        String rotatedToken = null;

        try {
            // Refresh Token을 새 Access·Refresh Token으로 갱신
            var response = authSessionService.refresh(issued.token());
            rotatedToken = response.refreshToken();

            assertEquals("Bearer", response.tokenType());
            assertEquals(900, response.accessTokenExpiresIn());
            assertFalse(issued.token().equals(response.refreshToken()), "Refresh Token이 교체되어야 합니다.");
            assertEquals(issued.expiresAt(), response.refreshTokenExpiresAt());

            // 발급된 Access Token의 사용자 식별자 확인
            var decoded = jwtDecoder.decode(response.accessToken());
            assertEquals(user.getId().toString(), decoded.getSubject());

            // 이전 토큰을 재사용하면 세션 전체가 폐기됨
            assertThrows(
                    BusinessException.class,
                    () -> authSessionService.refresh(issued.token())
            );

            String finalRotatedToken = rotatedToken;
            assertThrows(
                    BusinessException.class,
                    () -> authSessionService.refresh(finalRotatedToken)
            );

        } finally {
            if (rotatedToken != null) {
                authSessionService.logout(rotatedToken);
            } else {
                authSessionService.logout(issued.token());
            }
            accounts.deleteById(account.getId());
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    void logoutIsIdempotent() {
        User user = userRepository.save(new User("로그아웃테스트"));
        var issued = refreshTokenService.create(user.getId(), "test-device");

        try {
            // 동일 Refresh Session을 여러 번 폐기해도 오류가 없어야 함
            assertDoesNotThrow(
                    () -> authSessionService.logout(issued.token())
            );
            assertDoesNotThrow(
                    () -> authSessionService.logout(issued.token())
            );

            // 로그아웃 이후 새 토큰 발급 불가
            assertThrows(
                    BusinessException.class,
                    () -> authSessionService.refresh(issued.token())
            );

        } finally {
            authSessionService.logout(issued.token());
            userRepository.deleteById(user.getId());
        }
    }
}
