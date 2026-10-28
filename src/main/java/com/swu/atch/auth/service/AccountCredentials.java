package com.swu.atch.auth.service;

import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.domain.AuthProvider;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountCredentials {
    private final AuthAccountRepository accounts;
    private final AuthSecrets secrets;

    // 비밀번호 상태 변경 시 모든 기존 토큰의 stamp가 불일치하게 됨
    public String stamp(AuthAccount account) {
        return secrets.hmac("credential-stamp", account.getUser().getId() + ":" + account.getPasswordHash());
    }

    @Transactional(readOnly = true)
    public boolean isCurrent(Long userId, String stamp) {
        return accounts.findByUser_IdAndProvider(userId, AuthProvider.LOCAL)
                .map(account -> AuthSecrets.matches(stamp(account), stamp)).orElse(false);
    }

    public AuthAccount localAccount(Long userId) {
        return accounts.findByUser_IdAndProvider(userId, AuthProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public void requireCurrent(AuthAccount account, String stamp) {
        if (!AuthSecrets.matches(stamp(account), stamp)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
