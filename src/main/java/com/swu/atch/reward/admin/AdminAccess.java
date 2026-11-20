package com.swu.atch.reward.admin;

import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminAccess {
    private final AdminProperties properties;
    private final AccountCredentials credentials;
    private final UserRepository users;

    public boolean isCurrent(AdminPrincipal principal) {
        return properties.allows(principal.userId())
                && credentials.isCurrent(principal.userId(), principal.credentialStamp());
    }

    // 등록 커밋까지 비밀번호 변경·탈퇴와 같은 사용자 잠금을 적용한다.
    public void requireLocked(AdminPrincipal principal) {
        if (!properties.allows(principal.userId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        users.findByIdForUpdate(principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        credentials.requireCurrent(credentials.localAccount(principal.userId()), principal.credentialStamp());
    }
}
