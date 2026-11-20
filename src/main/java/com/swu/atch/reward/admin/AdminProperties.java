package com.swu.atch.reward.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Set;

@ConfigurationProperties("app.admin")
public record AdminProperties(@DefaultValue("false") boolean enabled, Set<Long> userIds) {
    public AdminProperties {
        userIds = userIds == null ? Set.of() : Set.copyOf(userIds);
    }

    public boolean allows(Long userId) {
        return enabled && userId != null && userIds.contains(userId);
    }
}
