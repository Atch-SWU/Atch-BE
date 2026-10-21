
package com.swu.atch.auth.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest extends IsolatedAuthIntegrationTest {

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void accessTokenCanBeIssuedAndVerified() {
        // 실제 사용자 저장 없이 JWT 발급 및 검증만 확인
        String accessToken = jwtTokenService.issueAccessToken(100L);

        Jwt decoded = jwtDecoder.decode(accessToken);

        assertEquals("100", decoded.getSubject());
        assertEquals(
                "https://atch.test.invalid",
                decoded.getIssuer().toString()
        );
        assertTrue(decoded.getAudience().contains("atch-api"));
        assertEquals("access", decoded.getClaimAsString("token_use"));
        assertEquals(
                Duration.ofMinutes(15),
                Duration.between(
                        decoded.getIssuedAt(),
                        decoded.getExpiresAt()
                )
        );
    }
}
