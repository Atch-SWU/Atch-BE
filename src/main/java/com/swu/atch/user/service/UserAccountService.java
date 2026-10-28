package com.swu.atch.user.service;

import com.swu.atch.auth.domain.AuthAccount;
import com.swu.atch.auth.refresh.RefreshTokenService;
import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.auth.service.PasswordResetChallenges;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAccountService {
    private final UserRepository users;
    private final AccountCredentials credentials;
    private final PasswordEncoder encoder;
    private final RefreshTokenService refreshTokens;
    private final PasswordResetChallenges challenges;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    @Transactional
    public String changeNickname(Long userId, String stamp, String nickname) {
        User user = lockUser(userId);
        credentials.requireCurrent(credentials.localAccount(userId), stamp);
        user.changeNickname(nickname);
        return user.getNickname();
    }

    @Transactional
    public void changePassword(Long userId, String stamp, String currentPassword, String newPassword) {
        lockUser(userId);
        var account = credentials.localAccount(userId);
        credentials.requireCurrent(account, stamp);
        verifyPassword(account, currentPassword);
        revokeSessions(userId);
        account.changePasswordHash(encoder.encode(newPassword));
    }

    @Transactional
    public void resetPassword(Long userId, String proofStamp, String newPassword) {
        if (users.findByIdForUpdate(userId).isEmpty()) throw invalidProof();
        var account = credentials.localAccount(userId);
        if (!com.swu.atch.auth.service.AuthSecrets.matches(credentials.stamp(account), proofStamp)) throw invalidProof();
        revokeSessions(userId);
        account.changePasswordHash(encoder.encode(newPassword));
    }

    @Transactional
    public void withdraw(Long userId, String stamp, String currentPassword) {
        lockUser(userId);
        var account = credentials.localAccount(userId);
        credentials.requireCurrent(account, stamp);
        verifyPassword(account, currentPassword);
        // 과거 스키마가 허용했던 교차 소유 참조는 다른 사용자 데이터 삭제로 우회하지 않음
        for (String table : new String[]{"todo_daily_snapshots", "time_prediction_experiments"}) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table
                    + " child JOIN todos t ON t.id=child.todo_id WHERE "
                    + "(child.user_id=? AND t.user_id<>?) OR (child.user_id<>? AND t.user_id=?)",
                    Integer.class, userId, userId, userId, userId);
            if (count != null && count > 0) throw new BusinessException(ErrorCode.RESOURCE_CONFLICT);
        }
        // Redis 실패 시 DB 삭제 전에 중단. DB 롤백 시 세션 폐기는 복구하지 않음
        revokeSessions(userId);
        try { challenges.purgeEmailState(account.getEmail()); }
        catch (DataAccessException ex) { throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE); }

        deleteByUser("reward_grants", userId);
        deleteByUser("reward_settlements", userId);
        deleteByUser("user_equipped_items", userId);
        deleteByUser("user_character_items", userId);
        jdbc.update("DELETE FROM todo_embeddings WHERE todo_id IN (SELECT id FROM todos WHERE user_id=?)", userId);
        deleteByUser("time_prediction_experiments", userId);
        jdbc.update("DELETE FROM sessions WHERE todo_id IN (SELECT id FROM todos WHERE user_id=?)", userId);
        deleteByUser("todo_daily_snapshots", userId);
        deleteByUser("todos", userId);
        deleteByUser("recurrence_rules", userId);
        deleteByUser("repeat_patterns", userId);
        deleteByUser("time_estimation_profiles", userId);
        deleteByUser("user_settings", userId);
        deleteByUser("auth_accounts", userId);
        jdbc.update("DELETE FROM users WHERE id=?", userId);
        entityManager.clear();
    }

    private void deleteByUser(String table, Long userId) {
        jdbc.update("DELETE FROM " + table + " WHERE user_id=?", userId);
    }

    private User lockUser(Long id) {
        return users.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private void verifyPassword(AuthAccount account, String password) {
        if (!encoder.matches(password, account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INVALID);
        }
    }

    private void revokeSessions(Long userId) {
        try { refreshTokens.revokeAllForUser(userId); }
        catch (DataAccessException ex) { throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE); }
    }

    private BusinessException invalidProof() { return new BusinessException(ErrorCode.PASSWORD_RESET_PROOF_INVALID); }
}
