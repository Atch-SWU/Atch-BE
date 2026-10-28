
package com.swu.atch.auth.service;

import io.swagger.v3.oas.annotations.media.Schema;

import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final StringRedisTemplate redisTemplate;
    private final EmailSender emailSender;
    private final RedisScript<Long> createScript;
    private final RedisScript<Long> verifyScript;
    private final RedisScript<Long> cancelScript;
    private final Duration codeTtl;
    private final Duration resendInterval;
    private final Duration proofTtl;
    private final String hmacSecret;
    private final RedisScript<Long> consumeProofScript;

    public EmailVerificationService(
            StringRedisTemplate redisTemplate,
            EmailSender emailSender,
            @Qualifier("createEmailCodeScript") RedisScript<Long> createScript,
            @Qualifier("verifyEmailCodeScript") RedisScript<Long> verifyScript,
            @Qualifier("cancelEmailCodeScript") RedisScript<Long> cancelScript,
            @Value("${app.auth.email-code-ttl}") Duration codeTtl,
            @Value("${app.auth.email-resend-interval}") Duration resendInterval,
            @Value("${app.auth.email-proof-ttl}") Duration proofTtl,
            @Value("${app.auth.email-code-hmac-secret}") String hmacSecret,
            @Qualifier("consumeEmailProofScript") RedisScript<Long> consumeProofScript
    ) {
        this.redisTemplate = redisTemplate;
        this.emailSender = emailSender;
        this.createScript = createScript;
        this.verifyScript = verifyScript;
        this.cancelScript = cancelScript;
        this.codeTtl = codeTtl;
        this.resendInterval = resendInterval;
        this.proofTtl = proofTtl;
        this.hmacSecret = hmacSecret;
        this.consumeProofScript = consumeProofScript;
    }

    // 6자리 인증코드 생성 및 이메일 발송
    public void sendCode(String email) {
        String normalized = normalizeEmail(email);
        String emailHash = sha256(normalized);
        String codeKey = "auth:email:code:" + emailHash;
        String cooldownKey = "auth:email:cooldown:" + emailHash;

        String code = String.format(
                Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000)
        );
        String generation = UUID.randomUUID().toString();

        Long result = redisTemplate.execute(
                createScript,
                List.of(codeKey, cooldownKey),
                hmacCode(normalized, code),
                generation,
                String.valueOf(codeTtl.toMillis()),
                String.valueOf(resendInterval.toMillis())
        );

        if (!Long.valueOf(1L).equals(result)) {
            throw new BusinessException(ErrorCode.EMAIL_RESEND_TOO_SOON);
        }

        try {
            emailSender.sendVerificationCode(normalized, code);
        } catch (MailException ex) {
            // SMTP 실패 시 이번 발급 건의 Redis 데이터 정리
            redisTemplate.execute(
                    cancelScript,
                    List.of(codeKey, cooldownKey),
                    generation
            );
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    // 인증코드 확인 후 일회용 가입 증명 발급
    public VerifiedEmail verifyCode(String email, String code) {
        String normalized = normalizeEmail(email);
        String emailHash = sha256(normalized);
        String codeKey = "auth:email:code:" + emailHash;

        String verificationToken = generateToken();
        String proofKey = "auth:email:proof:" + sha256(verificationToken);

        Long result = redisTemplate.execute(
                verifyScript,
                List.of(codeKey, proofKey),
                hmacCode(normalized, code),
                normalized,
                String.valueOf(proofTtl.toMillis()),
                String.valueOf(MAX_ATTEMPTS)
        );

        if (Long.valueOf(-2L).equals(result)) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_ATTEMPTS_EXCEEDED);
        }

        if (Long.valueOf(-3L).equals(result)) {
            throw new IllegalStateException("Verification token collision");
        }

        if (!Long.valueOf(1L).equals(result)) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_INVALID);
        }

        return new VerifiedEmail(
                verificationToken,
                proofTtl.toSeconds()
        );
    }


    // 회원가입 시 인증된 이메일을 확인하고 증명을 한 번만 소비
    public void consumeSignupProof(String email, String verificationToken) {
        if (verificationToken == null
                || !verificationToken.matches("[A-Za-z0-9_-]{43}")) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }

        String normalized = normalizeEmail(email);
        String proofKey = "auth:email:proof:" + sha256(verificationToken);

        Long result = redisTemplate.execute(
                consumeProofScript,
                List.of(proofKey),
                normalized
        );

        if (!Long.valueOf(1L).equals(result)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }
    }


    // 이메일 주소의 대소문자 차이를 통일
    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    // 서버 비밀값을 이용해 6자리 인증코드 보호
    private String hmacCode(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    hmacSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));

            byte[] digest = mac.doFinal(
                    (email + ":" + code).getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC calculation failed", ex);
        }
    }

    // Redis 키 및 인증 증명 식별자용 SHA-256
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    // 256비트 일회용 가입 증명 토큰 생성
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    public record VerifiedEmail(
            @Schema(description = "회원가입용 이메일 인증 증명. 회원가입 요청의 verificationToken에 복사합니다. 한 번만 사용할 수 있습니다.")
            String verificationToken,
            @Schema(description = "이메일 인증 증명의 유효시간(초)입니다.")
            long expiresIn) {
    }
}
