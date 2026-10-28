package com.swu.atch.auth.service;

import com.swu.atch.auth.domain.AuthProvider;
import com.swu.atch.auth.repository.AuthAccountRepository;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.user.service.UserAccountService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.core.task.TaskRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// HTTP 처리 경로에서는 계정 존재 조회·SMTP 결과를 기다리지 않음
@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private final PasswordResetChallenges challenges;
    private final AuthAccountRepository accounts;
    private final AccountCredentials credentials;
    private final EmailSender sender;
    private final TaskExecutor executor;
    private final UserAccountService userAccounts;

    public PasswordResetService(PasswordResetChallenges challenges, AuthAccountRepository accounts,
            AccountCredentials credentials, EmailSender sender,
            @Qualifier("passwordResetMailExecutor") TaskExecutor executor, UserAccountService userAccounts) {
        this.challenges=challenges; this.accounts=accounts; this.credentials=credentials;
        this.sender=sender; this.executor=executor; this.userAccounts=userAccounts;
    }

    public void requestCode(String email) {
        try {
            var code = challenges.issue(email);
            try { executor.execute(() -> deliver(code)); }
            catch (TaskRejectedException ex) {
                challenges.invalidate(code);
                throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
            }
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
    }

    private void deliver(PasswordResetChallenges.IssuedCode code) {
        try {
            var account = accounts.findByProviderAndEmailIgnoreCase(AuthProvider.LOCAL, code.email());
            if (account.isPresent() && challenges.bind(code, account.get().getUser().getId(), credentials.stamp(account.get()))) {
                sender.sendPasswordResetCode(code.email(), code.code());
            }
        } catch (RuntimeException ex) {
            // 예외 메시지·수신 주소·코드를 로그에 남기거나 HTTP 응답에 반영하지 않음
            log.warn("비밀번호 재설정 메일 처리 실패");
            try { challenges.invalidate(code); }
            catch (RuntimeException ignored) { log.warn("비밀번호 재설정 코드 정리 지연"); }
        }
    }

    public PasswordResetChallenges.ProofToken verify(String email, String code) {
        try { return challenges.verify(email, code); }
        catch (DataAccessException ex) { throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE); }
    }

    public void reset(String email, String token, String password) {
        try {
            var proof = challenges.consume(email, token);
            userAccounts.resetPassword(proof.userId(), proof.credentialStamp(), password);
        } catch (DataAccessException ex) { throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE); }
    }
}
