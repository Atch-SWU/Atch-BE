package com.swu.atch.auth.service;

import com.swu.atch.auth.domain.AuthProvider;
import com.swu.atch.auth.dto.request.LoginRequest;
import com.swu.atch.auth.dto.request.SignUpRequest;
import com.swu.atch.auth.dto.response.TokenResponse;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;

import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

// 이메일 회원가입·로그인 및 자동 로그인 처리 및 토큰 발급
@Slf4j
@Service
public class LocalAuthService {

    private final AuthAccountRepository authAccountRepository;
    private final EmailVerificationService emailVerificationService;
    private final LocalAuthPersistenceService persistenceService;
    private final AuthSessionService sessionService;
    private final PasswordEncoder passwordEncoder;

    public LocalAuthService(
            AuthAccountRepository authAccountRepository,
            EmailVerificationService emailVerificationService,
            LocalAuthPersistenceService persistenceService,
            AuthSessionService sessionService,
            PasswordEncoder passwordEncoder
    ) {
        this.authAccountRepository = authAccountRepository;
        this.emailVerificationService = emailVerificationService;
        this.persistenceService = persistenceService;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
    }

    // 이메일 인증 완료 후 회원가입 및 자동 로그인

    public TokenResponse signUp(SignUpRequest request) {
        String email = normalizeEmail(request.email());

        if (authAccountRepository.existsByProviderAndEmailIgnoreCase(
                AuthProvider.LOCAL, email
        )) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        // 인증 증명 원자적 검증 및 소비
        emailVerificationService.consumeSignupProof(
                email,
                request.verificationToken()
        );

        String passwordHash = passwordEncoder.encode(request.password());
        Long userId;

        try {
            // 별도 @Transactional 서비스에서 DB 저장·커밋
            userId = persistenceService.register(
                    email,
                    request.nickname(),
                    passwordHash
            );
        } catch (DataIntegrityViolationException ex) {
            // 동시 가입으로 동일 이메일이 생성됐는지 재확인
            try {
                if (authAccountRepository.existsByProviderAndEmailIgnoreCase(
                        AuthProvider.LOCAL, email
                )) {
                    throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
                }
            } catch (DataAccessException lookupEx) {
                log.error("회원가입 실패 후 이메일 중복 조회 오류", lookupEx);
            }

            log.error("회원가입 DB 제약조건 오류", ex);
            throw new BusinessException(ErrorCode.SIGNUP_FAILED_REVERIFY);

        } catch (RuntimeException ex) {
            // DB 저장 또는 트랜잭션 커밋 실패
            log.error("회원가입 DB 처리 실패", ex);
            throw new BusinessException(ErrorCode.SIGNUP_FAILED_REVERIFY);
        }

        try {
            // DB 커밋 성공 이후 자동 로그인 토큰 발급
            return sessionService.issue(userId, request.deviceId(), null);
        } catch (RuntimeException ex) {
            // 계정 생성은 완료됐으므로 회원가입을 다시 시도하면 안 됨
            log.error("회원가입 완료 후 자동 로그인 실패, userId={}", userId, ex);
            throw new BusinessException(
                    ErrorCode.SIGNUP_COMPLETED_LOGIN_FAILED
            );
        }
    }


    // 이메일·비밀번호 인증 후 토큰 발급
    public TokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        var authenticated = persistenceService.authenticate(
                email,
                request.password()
        );

        return sessionService.issue(authenticated.userId(), request.deviceId(), authenticated.credentialStamp());
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
