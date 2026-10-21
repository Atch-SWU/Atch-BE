
package com.swu.atch.auth.service;

import com.swu.atch.auth.refresh.RefreshTokenService;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuthSessionServiceTest {

    @Autowired
    private AuthSessionService authSessionService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void refreshIssuesTokensAndRejectsReuse() {
        User user = userRepository.save(new User("테스트사용자"));
        var issued = refreshTokenService.create(user.getId(), "test-device");
        String rotatedToken = null;

        try {
            // Refresh Token을 새 Access·Refresh Token으로 갱신
            var response = authSessionService.refresh(issued.token());
            rotatedToken = response.refreshToken();

            assertEquals("Bearer", response.tokenType());
            assertEquals(900, response.accessTokenExpiresIn());
            assertNotEquals(issued.token(), response.refreshToken());
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
