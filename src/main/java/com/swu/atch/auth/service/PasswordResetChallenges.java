package com.swu.atch.auth.service;

import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PasswordResetChallenges {
    private static final RedisScript<Long> CREATE = script("create-reset-code.lua");
    private static final RedisScript<Long> BIND = script("bind-reset-code.lua");
    private static final RedisScript<Long> VERIFY = script("verify-reset-code.lua");
    private static final RedisScript<Long> INVALIDATE = script("invalidate-reset-code.lua");
    private static final RedisScript<String> CONSUME = RedisScript.of(
            new ClassPathResource("redis/consume-reset-proof.lua"), String.class);
    private static final RedisScript<Long> DELETE_VALUE = RedisScript.of(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0", Long.class);
    private final StringRedisTemplate redis;
    private final AuthSecrets secrets;
    private final Duration codeTtl;
    private final Duration cooldown;
    private final Duration proofTtl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetChallenges(StringRedisTemplate redis, AuthSecrets secrets,
            @Value("${app.auth.email-code-ttl}") Duration codeTtl,
            @Value("${app.auth.email-resend-interval}") Duration cooldown,
            @Value("${app.auth.email-proof-ttl}") Duration proofTtl) {
        this.redis = redis; this.secrets = secrets;
        this.codeTtl = codeTtl; this.cooldown = cooldown; this.proofTtl = proofTtl;
    }

    public IssuedCode issue(String email) {
        String normalized = normalize(email);
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        var issued = new IssuedCode(normalized, code, UUID.randomUUID().toString());
        Long result = redis.execute(CREATE, List.of(codeKey(normalized), cooldownKey(normalized)),
                codeHash(normalized, code), issued.generation(),
                String.valueOf(codeTtl.toMillis()), String.valueOf(cooldown.toMillis()));
        if (!Long.valueOf(1).equals(result)) throw new BusinessException(ErrorCode.EMAIL_RESEND_TOO_SOON);
        return issued;
    }

    public boolean bind(IssuedCode code, Long userId, String stamp) {
        return Long.valueOf(1).equals(redis.execute(BIND, List.of(codeKey(code.email())),
                code.generation(), userId.toString(), stamp));
    }

    public void invalidate(IssuedCode code) {
        redis.execute(INVALIDATE, List.of(codeKey(code.email())), code.generation());
    }

    public ProofToken verify(String email, String code) {
        String normalized = normalize(email);
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Long result = redis.execute(VERIFY, List.of(codeKey(normalized), proofKey(token)),
                codeHash(normalized, code), AuthSecrets.sha256(normalized), String.valueOf(proofTtl.toMillis()));
        if (Long.valueOf(-2).equals(result)) throw new BusinessException(ErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
        if (!Long.valueOf(1).equals(result)) throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID);
        return new ProofToken(token, proofTtl.toSeconds());
    }

    public VerifiedAccount consume(String email, String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidProof();
        String value = redis.execute(CONSUME, List.of(proofKey(token)), AuthSecrets.sha256(normalize(email)));
        if (value == null) throw invalidProof();
        String[] fields = value.split(":", 3);
        if (fields.length != 3) throw invalidProof();
        return new VerifiedAccount(Long.parseLong(fields[1]), fields[2]);
    }

    // 탈퇴 시 해당 이메일의 가입·재설정 코드 및 미사용 증명도 정리
    public void purgeEmailState(String email) {
        String normalized = normalize(email);
        String hash = AuthSecrets.sha256(normalized);
        redis.delete(List.of(codeKey(normalized), cooldownKey(normalized),
                "auth:email:code:" + hash, "auth:email:cooldown:" + hash));
        purgeMatchingProofs("auth:reset:proof:*", hash + ":", false);
        purgeMatchingProofs("auth:email:proof:*", normalized, true);
    }

    private void purgeMatchingProofs(String pattern, String expected, boolean exact) {
        try (var cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(100).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next(); String value = redis.opsForValue().get(key);
                if (value != null && (exact ? value.equals(expected) : value.startsWith(expected))) {
                    redis.execute(DELETE_VALUE, List.of(key), value);
                }
            }
        }
    }

    private String codeHash(String email, String code) { return secrets.hmac("password-reset-code", email + ":" + code); }
    private String codeKey(String email) { return "auth:reset:code:" + AuthSecrets.sha256(email); }
    private String cooldownKey(String email) { return "auth:reset:cooldown:" + AuthSecrets.sha256(email); }
    private String proofKey(String token) { return "auth:reset:proof:" + AuthSecrets.sha256(token); }
    private static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    private static RedisScript<Long> script(String path) { return RedisScript.of(new ClassPathResource("redis/" + path), Long.class); }
    private static BusinessException invalidProof() { return new BusinessException(ErrorCode.PASSWORD_RESET_PROOF_INVALID); }

    public record IssuedCode(String email, String code, String generation) {
        @Override public String toString() { return "IssuedCode[REDACTED]"; }
    }
    public record ProofToken(String resetToken, long expiresIn) {
        @Override public String toString() { return "ProofToken[REDACTED]"; }
    }
    public record VerifiedAccount(Long userId, String credentialStamp) { }
}
