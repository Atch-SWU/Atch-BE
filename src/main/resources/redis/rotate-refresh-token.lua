
-- 현재 세션의 토큰 해시 조회
local currentHash = redis.call('HGET', KEYS[1], 'tokenHash')

if not currentHash then
    return 0
end

-- 기존 토큰이 일치하지 않으면 재사용 가능성으로 판단하고 세션 폐기
if currentHash ~= ARGV[1] then
    redis.call('DEL', KEYS[1])
    redis.call('SREM', KEYS[2], ARGV[4])
    if redis.call('SCARD', KEYS[2]) == 0 then redis.call('DEL', KEYS[2]) end
    return -1
end

-- 토큰 해시 및 마지막 갱신 시각 교체
redis.call('HSET', KEYS[1],
    'tokenHash', ARGV[2],
    'lastRotatedAt', ARGV[3]
)

-- 기존 TTL을 변경하지 않음
return 1
