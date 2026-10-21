
-- 현재 세션의 토큰 해시 조회
local currentHash = redis.call('HGET', KEYS[1], 'tokenHash')

-- 유효한 토큰을 제시한 경우에만 세션 삭제
if currentHash and currentHash == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end

return 0
