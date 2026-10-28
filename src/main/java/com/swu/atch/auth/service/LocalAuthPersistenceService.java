
package com.swu.atch.auth.service;

import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.domain.AuthProvider;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.setting.domain.UserSetting;
import com.swu.atch.setting.repository.UserSettingRepository;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// PostgreSQL 트랜잭션 내 사용자 생성 및 로그인 정보 조회·수정
// LOCAL 사용자 데이터 저장 및 로그인 검증
@Service
public class LocalAuthPersistenceService {

    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;
    private final UserSettingRepository userSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountCredentials credentials;

    public LocalAuthPersistenceService(
            UserRepository userRepository,
            AuthAccountRepository authAccountRepository,
            UserSettingRepository userSettingRepository,
            PasswordEncoder passwordEncoder,
            AccountCredentials credentials
    ) {
        this.userRepository = userRepository;
        this.authAccountRepository = authAccountRepository;
        this.userSettingRepository = userSettingRepository;
        this.passwordEncoder = passwordEncoder;
        this.credentials = credentials;
    }

    // USER·AUTH_ACCOUNT·USER_SETTING을 하나의 트랜잭션에서 생성
    @Transactional
    public Long register(
            String email,
            String nickname,
            String passwordHash
    ) {
        User user = userRepository.save(new User(nickname));

        authAccountRepository.save(
                AuthAccount.local(user, email, passwordHash)
        );

        userSettingRepository.save(new UserSetting(user));

        // 커밋이 완료된 뒤 호출자에게 사용자 ID 반환
        return user.getId();
    }

    // 이메일·비밀번호 검증 및 로그인 시각 기록
    @Transactional
    public AuthenticatedAccount authenticate(String email, String password) {
        Long userId = authAccountRepository.findUserIdByProviderAndEmail(AuthProvider.LOCAL, email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        // 잠금 대기 중 비밀번호가 변경됐을 수 있으므로 최신 행을 다시 읽음
        AuthAccount account = authAccountRepository.findByUser_IdAndProvider(userId, AuthProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(
                password,
                account.getPasswordHash()
        )) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        account.recordLogin();

        return new AuthenticatedAccount(userId, credentials.stamp(account));
    }

    public record AuthenticatedAccount(Long userId, String credentialStamp) { }
}
