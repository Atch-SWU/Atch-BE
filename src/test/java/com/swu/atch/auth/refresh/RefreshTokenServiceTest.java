
package com.swu.atch.auth.refresh;

import com.swu.atch.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.swu.atch.support.IsolatedAuthIntegrationTest;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class RefreshTokenServiceTest extends IsolatedAuthIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void refreshTokenRotationAndReuseDetection() {
        // 최초 토큰 생성
        var issued = refreshTokenService.create(
                100L,
                "test-device-1"
        );

        // 기존 토큰을 새 토큰으로 교체
        var rotated = refreshTokenService.rotate(issued.token());

        assertFalse(issued.token().equals(rotated.token()), "Refresh Token이 교체되어야 합니다.");
        assertEquals(100L, rotated.userId());
        assertEquals(issued.expiresAt(), rotated.expiresAt());

        // 이전 토큰 재사용 시 현재 세션까지 폐기
        assertThrows(
                BusinessException.class,
                () -> refreshTokenService.rotate(issued.token())
        );

        // 폐기된 세션에서는 새 토큰도 사용 불가
        assertThrows(
                BusinessException.class,
                () -> refreshTokenService.rotate(rotated.token())
        );
    }

    @Test
    void logoutRevokesRefreshSession() {
        var issued = refreshTokenService.create(
                200L,
                "test-device-2"
        );

        // 현재 세션 삭제
        assertTrue(refreshTokenService.revoke(issued.token()));

        // 동일 세션 재삭제 시 false 반환
        assertFalse(refreshTokenService.revoke(issued.token()));

        // 삭제된 세션으로 토큰 재발급 불가
        assertThrows(
                BusinessException.class,
                () -> refreshTokenService.rotate(issued.token())
        );
    }


    // SHA-256 해시 및 TTL 검증 테스트
    @Test
    void refreshTokenHashAndExpirationArePreserved() throws Exception {
        var issued = refreshTokenService.create(300L, "test-device-3");
        String key = "auth:refresh:" + issued.token().split("\\.")[0];

        try {
            // Redis에 저장된 Refresh Session 조회
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);

            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(issued.token().getBytes(StandardCharsets.UTF_8));

            String expectedHash = HexFormat.of().formatHex(digest);

            // 원문 대신 SHA-256 해시가 저장됐는지 확인
            assertTrue(expectedHash.equals(fields.get("tokenHash")), "Refresh Token 해시가 일치해야 합니다.");
            assertFalse(fields.containsValue(issued.token()));

            // 발급 시각부터 정확히 14일인지 확인. Mac과 Redis의 시계 차이에 의존하지 않는다.
            assertEquals(
                    String.valueOf(issued.expiresAt().toEpochMilli()),
                    fields.get("absoluteExpiresAt")
            );
            assertEquals(Duration.ofDays(14).toMillis(),
                    issued.expiresAt().toEpochMilli() - Long.parseLong(fields.get("issuedAt").toString()));
            var expirationScript = new DefaultRedisScript<>(
                    "return redis.call('PEXPIRETIME', KEYS[1])", Long.class);
            Long expirationBefore = redisTemplate.execute(expirationScript, List.of(key));
            assertEquals(Long.valueOf(issued.expiresAt().toEpochMilli()), expirationBefore);

            Long ttlBefore = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

            assertNotNull(ttlBefore);
            assertTrue(ttlBefore > 0);

            Thread.sleep(100);

            // Rotation 후에도 TTL이 연장되지 않는지 확인
            var rotated = refreshTokenService.rotate(issued.token());

            Long ttlAfter = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

            assertNotNull(ttlAfter);
            assertTrue(ttlAfter > 0);
            assertTrue(ttlAfter <= ttlBefore);
            assertEquals(expirationBefore, redisTemplate.execute(expirationScript, List.of(key)));
            assertEquals(issued.expiresAt(), rotated.expiresAt());

            // 새 토큰 해시로 교체됐는지 확인
            byte[] newDigest = MessageDigest.getInstance("SHA-256")
                    .digest(rotated.token().getBytes(StandardCharsets.UTF_8));

            assertTrue(HexFormat.of().formatHex(newDigest).equals(
                    redisTemplate.opsForHash().get(key, "tokenHash")), "갱신 해시가 일치해야 합니다.");

        } finally {
            // 테스트 성공·실패 여부와 관계없이 생성한 세션 정리
            redisTemplate.delete(key);
        }
    }


    // 동시 Rotation 검증 테스트
    @Test
    void concurrentRotationAllowsOnlyOneSuccess() throws Exception {
        var issued = refreshTokenService.create(400L, "test-device-4");
        String key = "auth:refresh:" + issued.token().split("\\.")[0];

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> attempt = () -> {
            ready.countDown();

            // 두 스레드가 동시에 Rotation 요청을 시작하도록 대기
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test timed out");
            }

            try {
                refreshTokenService.rotate(issued.token());
                return true;
            } catch (BusinessException ex) {
                return false;
            }
        };

        try {
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            boolean firstSuccess = first.get(5, TimeUnit.SECONDS);
            boolean secondSuccess = second.get(5, TimeUnit.SECONDS);

            // 동일한 Refresh Token으로는 최대 한 번만 갱신 성공
            int successCount = (firstSuccess ? 1 : 0)
                    + (secondSuccess ? 1 : 0);

            assertEquals(1, successCount);

            // 두 번째 재사용 감지로 해당 Refresh Session 폐기
            assertEquals(Boolean.FALSE, redisTemplate.hasKey(key));

        } finally {
            start.countDown();
            executor.shutdownNow();
            redisTemplate.delete(key);
        }
    }

}
