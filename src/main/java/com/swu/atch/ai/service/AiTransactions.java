package com.swu.atch.ai.service;

import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.*;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Function;

@Component
public class AiTransactions {
    private final TransactionTemplate tx;
    private final UserRepository users;
    private final AccountCredentials credentials;

    public AiTransactions(
            PlatformTransactionManager manager,
            UserRepository users,
            AccountCredentials credentials) {
        this.tx = new TransactionTemplate(manager);
        this.users = users;
        this.credentials = credentials;
    }

    // 기존 계정·타이머와 동일하게 User부터 잠근다. 외부 호출을 이 함수 안에 넣지 않는다.
    public <T> T locked(Long id, String stamp, Function<User, T> work) {
        try {
            return tx.execute(
                    status -> {
                        var user =
                                users.findByIdForUpdate(id)
                                        .orElseThrow(
                                                () ->
                                                        new BusinessException(
                                                                ErrorCode.UNAUTHORIZED));
                        credentials.requireCurrent(credentials.localAccount(id), stamp);
                        return work.apply(user);
                    });
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}
