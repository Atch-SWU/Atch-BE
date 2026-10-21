
package com.swu.atch.setting.repository;

import com.swu.atch.setting.domain.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingRepository extends JpaRepository<UserSetting, Long> {
}
