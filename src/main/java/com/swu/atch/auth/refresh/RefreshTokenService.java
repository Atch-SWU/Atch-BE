
package com.swu.atch.auth.refresh;

import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ScanOptions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.temporal.ChronoUnit;

// Redis 기반 Refresh Token 발급·갱신·폐기
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final int SECRET_BYTES = 32;
    private static final String USER_INDEX_PREFIX = "auth:refresh:user:";
    private static final RedisScript<Long> REVOKE_USER = RedisScript.of(
            new ClassPathResource("redis/revoke-user-sessions.lua"), Long.class);
    private static final RedisScript<Long> DELETE_OWNED = RedisScript.of(
            "if redis.call('HGET', KEYS[1], 'userId') == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0",
            Long.class);

    private final SecureRandom secureRandom = new SecureRandom();
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> createScript;
    private final RedisScript<Long> rotateScript;
    private final RedisScript<Long> revokeScript;
    private final Duration refreshTokenTtl;

    public RefreshTokenService(
            StringRedisTemplate redisTemplate,
            @Qualifier("createRefreshSessionScript")
            RedisScript<Long> createScript,
            @Qualifier("rotateRefreshTokenScript")
            RedisScript<Long> rotateScript,
            @Qualifier("revokeRefreshSessionScript")
            RedisScript<Long> revokeScript,
            @Value("${app.auth.refresh-token-ttl}")
            Duration refreshTokenTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.createScript = createScript;
        this.rotateScript = rotateScript;
        this.revokeScript = revokeScript;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    // 로그인 성공 후 Refresh Session 생성
    public IssuedRefreshToken create(Long userId, String deviceId) {
        return create(userId, deviceId, "");
    }

    public IssuedRefreshToken create(Long userId, String deviceId, String credentialStamp) {
        Objects.requireNonNull(userId, "userId is required");

        if (userId <= 0 || deviceId == null
                || deviceId.isBlank() || deviceId.length() > 128) {
            throw new IllegalArgumentException("Invalid session information");
        }

        String sessionId = UUID.randomUUID().toString();
        String refreshToken = generateToken(sessionId);

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS); // Redis 저장 정밀도와 일치
        Instant expiresAt = issuedAt.plus(refreshTokenTtl);

        Long result = redisTemplate.execute(
                createScript,
                List.of(redisKey(sessionId), USER_INDEX_PREFIX + userId),
                userId.toString(),
                deviceId,
                hashToken(refreshToken),
                String.valueOf(issuedAt.toEpochMilli()),
                String.valueOf(expiresAt.toEpochMilli()),
                credentialStamp,
                sessionId,
                String.valueOf(refreshTokenTtl.toMillis())
        );

        if (!Long.valueOf(1L).equals(result)) {
            throw new IllegalStateException("Refresh Session creation failed");
        }

        return new IssuedRefreshToken(refreshToken, expiresAt);
    }

    // 기존 토큰 검증 후 새 토큰으로 원자적 교체
    public RotatedRefreshToken rotate(String refreshToken) {
        ParsedToken parsed = parseToken(refreshToken);
        String key = redisKey(parsed.sessionId());

        Object userId = redisTemplate.opsForHash().get(key, "userId");
        Object expiresAt = redisTemplate.opsForHash()
                .get(key, "absoluteExpiresAt");
        Object stamp = redisTemplate.opsForHash().get(key, "credentialStamp");

        if (userId == null || expiresAt == null) {
            throw invalidSession();
        }

        String newToken = generateToken(parsed.sessionId());

        Long result = redisTemplate.execute(
                rotateScript,
                List.of(key, USER_INDEX_PREFIX + userId),
                hashToken(refreshToken),
                hashToken(newToken),
                String.valueOf(Instant.now().toEpochMilli()),
                parsed.sessionId()
        );

        if (!Long.valueOf(1L).equals(result)) {
            throw invalidSession();
        }

        return new RotatedRefreshToken(
                newToken,
                Long.parseLong(userId.toString()),
                Instant.ofEpochMilli(
                        Long.parseLong(expiresAt.toString())
                ), stamp == null ? "" : stamp.toString()
        );
    }

    // 현재 토큰과 일치하는 로그인 세션만 폐기
    public boolean revoke(String refreshToken) {
        ParsedToken parsed = parseToken(refreshToken);
        Object userId = redisTemplate.opsForHash().get(redisKey(parsed.sessionId()), "userId");
        if (userId == null) return false;

        Long result = redisTemplate.execute(
                revokeScript,
                List.of(redisKey(parsed.sessionId()), USER_INDEX_PREFIX + userId),
                hashToken(refreshToken), parsed.sessionId()
        );

        return Long.valueOf(1L).equals(result);
    }

    // 토큰 갱신 전 해당 사용자의 User 행 잠금을 획득하기 위한 식별자
    public Long ownerOf(String refreshToken) {
        Object value = redisTemplate.opsForHash().get(redisKey(parseToken(refreshToken).sessionId()), "userId");
        if (value == null) throw invalidSession();
        return Long.parseLong(value.toString());
    }

    public void revokeAllForUser(Long userId) {
        redisTemplate.execute(REVOKE_USER, List.of(USER_INDEX_PREFIX + userId), userId.toString(), KEY_PREFIX);
        // 목록 도입 전에 발급된 세션도 삭제. KEYS/FLUSH 없이 UUID 형태의 기존 세션만 SCAN
        try (var cursor = redisTemplate.scan(ScanOptions.scanOptions()
                .match("auth:refresh:????????-????-????-????-????????????").count(100).build())) {
            while (cursor.hasNext()) {
                redisTemplate.execute(DELETE_OWNED, List.of(cursor.next()), userId.toString());
            }
        }
    }

    // 세션 식별자와 256비트 랜덤 값을 조합해 토큰 생성
    private String generateToken(String sessionId) {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);

        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(secret);

        return sessionId + "." + encoded;
    }

    // 클라이언트가 전달한 토큰 형식 검증
    private ParsedToken parseToken(String token) {
        if (token == null || token.length() > 256) {
            throw invalidSession();
        }

        int separator = token.indexOf('.');

        if (separator <= 0 || separator == token.length() - 1) {
            throw invalidSession();
        }

        String sessionId = token.substring(0, separator);
        String secret = token.substring(separator + 1);

        try {
            UUID uuid = UUID.fromString(sessionId);

            if (!uuid.toString().equals(sessionId)
                    || secret.length() != 43
                    || Base64.getUrlDecoder().decode(secret).length
                    != SECRET_BYTES) {
                throw invalidSession();
            }
        } catch (IllegalArgumentException ex) {
            throw invalidSession();
        }

        return new ParsedToken(sessionId);
    }

    // Refresh Token 원문 대신 SHA-256 해시 저장
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String redisKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    private BusinessException invalidSession() {
        return new BusinessException(ErrorCode.REFRESH_SESSION_INVALID);
    }

    public record IssuedRefreshToken(
            String token,
            Instant expiresAt
    ) {
    }

    public record RotatedRefreshToken(
            String token,
            Long userId,
            Instant expiresAt,
            String credentialStamp
    ) {
    }

    private record ParsedToken(String sessionId) {
    }
}
