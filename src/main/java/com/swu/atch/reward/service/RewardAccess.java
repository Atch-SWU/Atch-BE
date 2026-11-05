package com.swu.atch.reward.service;

import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.*;
import com.swu.atch.report.service.DailySettlementService;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class RewardAccess {
    private final DailySettlementService daily;
    private final UserRepository users;
    private final AccountCredentials credentials;
    private final PlatformTransactionManager transactions;

    public <T> T execute(Long id, String stamp, Function<Context, T> action) {
        try {
            // 준비 묶음은 먼저 커밋해 긴 공백도 재조회할 때마다 전진시킨다.
            if (!daily.prepare(id, stamp)) throw new BusinessException(ErrorCode.REWARD_SETTLEMENT_PENDING);
            return new TransactionTemplate(transactions).execute(status -> {
                var user = users.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
                credentials.requireCurrent(credentials.localAccount(id), stamp);
                Instant now = daily.requireCurrentLocked(user);
                return action.apply(new Context(user, now));
            });
        } catch (DataAccessException ex) {
            throw new BusinessException(ErrorCode.REWARD_SETTLEMENT_PENDING);
        }
    }

    public record Context(User user, Instant now) { }
}
