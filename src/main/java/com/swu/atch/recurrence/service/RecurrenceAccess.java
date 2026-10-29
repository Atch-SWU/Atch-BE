package com.swu.atch.recurrence.service;

import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.*;
import com.swu.atch.setting.domain.UserSetting;
import com.swu.atch.setting.repository.UserSettingRepository;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RecurrenceAccess {
    private final UserRepository users;
    private final AccountCredentials credentials;
    private final UserSettingRepository settings;

    // 모든 반복 쓰기와 조회 최신화는 같은 사용자 행에서 직렬화한다.
    public User lock(Long id, String stamp) {
        var user = users.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        credentials.requireCurrent(credentials.localAccount(id), stamp);
        return user;
    }
    public UserSetting settings(User user) {
        return settings.findById(user.getId()).orElseGet(() -> settings.save(new UserSetting(user)));
    }
    public void requireRecommendation(User user) {
        if (!settings(user).isRepeatRecommendationEnabled())
            throw new BusinessException(ErrorCode.RECURRENCE_RECOMMENDATION_DISABLED);
    }
}
