package com.swu.atch.session.service;

import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.*;
import com.swu.atch.setting.domain.UserSetting;
import com.swu.atch.setting.dto.request.TimerSettingsUpdateRequest;
import com.swu.atch.setting.dto.response.TimerSettingsResponse;
import com.swu.atch.setting.repository.UserSettingRepository;
import com.swu.atch.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TimerSettingService {
    private final UserRepository users;
    private final UserSettingRepository settings;
    private final AccountCredentials credentials;
    public TimerSettingsResponse get(Long id, String stamp) { return response(locked(id, stamp)); }
    public TimerSettingsResponse update(Long id, String stamp, TimerSettingsUpdateRequest request) {
        if (request.taskSwitchRestrictionEnabled() == null && request.taskSwitchWaitMinutes() == null
                || request.taskSwitchWaitMinutes() != null && request.taskSwitchWaitMinutes() <= 0)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var setting = locked(id, stamp);
        setting.changeTimerSettings(request.taskSwitchRestrictionEnabled(), request.taskSwitchWaitMinutes());
        settings.flush(); return response(setting);
    }
    private UserSetting locked(Long id, String stamp) {
        var user = users.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        credentials.requireCurrent(credentials.localAccount(id), stamp);
        return settings.findById(id).orElseGet(() -> settings.saveAndFlush(new UserSetting(user)));
    }
    private TimerSettingsResponse response(UserSetting s) {
        return new TimerSettingsResponse(s.isTaskSwitchRestrictionEnabled(), s.getTaskSwitchWaitMinutes(), s.getUpdatedAt());
    }
}
