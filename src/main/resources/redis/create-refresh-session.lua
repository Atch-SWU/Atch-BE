
-- 세션 키 중복 여부 확인
if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end

-- Refresh Session 생성
redis.call('HSET', KEYS[1],
    'userId', ARGV[1],
    'deviceId', ARGV[2],
    'tokenHash', ARGV[3],
    'issuedAt', ARGV[4],
    'lastRotatedAt', ARGV[4],
    'absoluteExpiresAt', ARGV[5]
)

-- 최초 발급 시점 기준 절대 만료시간 지정
redis.call('PEXPIREAT', KEYS[1], ARGV[5])

return 1
