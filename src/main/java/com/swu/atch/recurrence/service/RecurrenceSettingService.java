package com.swu.atch.recurrence.service;

import com.swu.atch.recurrence.dto.request.RecurrenceSettingsUpdateRequest;
import com.swu.atch.recurrence.dto.response.RecurrenceSettingsResponse;
import com.swu.atch.setting.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RecurrenceSettingService {
    private final RecurrenceAccess access;
    private final UserSettingRepository settings;
    public RecurrenceSettingsResponse get(Long id, String stamp) {
        var value = access.settings(access.lock(id, stamp));
        settings.flush();
        return new RecurrenceSettingsResponse(value.isRepeatRecommendationEnabled(), value.getUpdatedAt());
    }
    public RecurrenceSettingsResponse update(Long id, String stamp, RecurrenceSettingsUpdateRequest request) {
        if (request.repeatRecommendationEnabled() == null) throw RecurrencePolicy.invalid();
        var value = access.settings(access.lock(id, stamp));
        value.changeRepeatRecommendation(request.repeatRecommendationEnabled());
        settings.flush();
        return new RecurrenceSettingsResponse(value.isRepeatRecommendationEnabled(), value.getUpdatedAt());
    }
}
