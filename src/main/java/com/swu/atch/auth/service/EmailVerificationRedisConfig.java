
package com.swu.atch.auth.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class EmailVerificationRedisConfig {

    @Bean
    public RedisScript<Long> createEmailCodeScript() {
        return RedisScript.of(
                new ClassPathResource("redis/create-email-code.lua"),
                Long.class
        );
    }

    @Bean
    public RedisScript<Long> verifyEmailCodeScript() {
        return RedisScript.of(
                new ClassPathResource("redis/verify-email-code.lua"),
                Long.class
        );
    }

    @Bean
    public RedisScript<Long> cancelEmailCodeScript() {
        return RedisScript.of(
                new ClassPathResource("redis/cancel-email-code.lua"),
                Long.class
        );
    }

    @Bean
    public RedisScript<Long> consumeEmailProofScript() {
        return RedisScript.of(
                new ClassPathResource("redis/consume-email-proof.lua"),
                Long.class
        );
    }
}
