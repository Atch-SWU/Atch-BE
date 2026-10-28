package com.swu.atch.ai.service;

import com.swu.atch.common.exception.*;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class AiRateLimiter {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> LIMIT =
            new DefaultRedisScript<>(
                    """
for i=1,#KEYS do
  if tonumber(redis.call('GET',KEYS[i]) or '0') + tonumber(ARGV[2*i-1]) > tonumber(ARGV[2*i]) then return 0 end
end
for i=1,#KEYS do
  redis.call('INCRBY',KEYS[i],ARGV[2*i-1]); redis.call('EXPIRE',KEYS[i],tonumber(ARGV[2*#KEYS+i]))
end
return 1
""",
                    Long.class);

    public AiRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void acquire(Long user, boolean draft, int items) {
        long epoch = Instant.now().getEpochSecond();
        String prefix = "ai:limit:" + user + ":" + (draft ? "draft:" : "execute:");
        var keys =
                List.of(
                        prefix + "requests:" + epoch / 60,
                        prefix + "items:" + epoch / 60,
                        prefix + "daily:" + epoch / 86400);
        try {
            Long allowed =
                    redis.execute(
                            LIMIT,
                            keys,
                            "1",
                            draft ? "30" : "6",
                            String.valueOf(items),
                            draft ? "30" : "60",
                            String.valueOf(items),
                            "200",
                            "120",
                            "120",
                            "172800");
            if (!Long.valueOf(1).equals(allowed))
                throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
        } catch (org.springframework.dao.DataAccessException ex) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}
