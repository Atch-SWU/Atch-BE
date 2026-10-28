
-- 현재 세션의 토큰 해시 조회
local currentHash = redis.call('HGET', KEYS[1], 'tokenHash')

-- 유효한 토큰을 제시한 경우에만 세션 삭제
if currentHash and currentHash == ARGV[1] then
    redis.call('SREM', KEYS[2], ARGV[2])
    if redis.call('SCARD', KEYS[2]) == 0 then redis.call('DEL', KEYS[2]) end
    return redis.call('DEL', KEYS[1])
end

return 0
