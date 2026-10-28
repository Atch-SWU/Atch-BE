-- User 행 잠금 내에서 호출. 해당 사용자의 세션만 삭제
local sessions = redis.call('SMEMBERS', KEYS[1])
local removed = 0
for _, id in ipairs(sessions) do
    local key = ARGV[2] .. id
    if redis.call('HGET', key, 'userId') == ARGV[1] then
        removed = removed + redis.call('DEL', key)
    end
end
redis.call('DEL', KEYS[1])
return removed
