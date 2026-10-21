
package com.swu.atch.auth.refresh;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

// Refresh Session 처리용 Redis Lua 스크립트 등록
@Configuration
public class RefreshRedisConfig {

    @Bean
    public RedisScript<Long> createRefreshSessionScript() {
        return RedisScript.of(
                new ClassPathResource("redis/create-refresh-session.lua"),
                Long.class
        );
    }

    @Bean
    public RedisScript<Long> rotateRefreshTokenScript() {
        return RedisScript.of(
                new ClassPathResource("redis/rotate-refresh-token.lua"),
                Long.class
        );
    }

    @Bean
    public RedisScript<Long> revokeRefreshSessionScript() {
        return RedisScript.of(
                new ClassPathResource("redis/revoke-refresh-session.lua"),
                Long.class
        );
    }
}
