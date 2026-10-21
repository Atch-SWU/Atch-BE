local storedHash = redis.call('HGET', KEYS[1], 'codeHash')

if not storedHash then
    return 0
end

-- 인증코드 불일치 시 오입력 횟수 증가
if storedHash ~= ARGV[1] then
    local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)

    if attempts >= tonumber(ARGV[4]) then
        redis.call('DEL', KEYS[1])
        return -2
    end

    return -1
end

-- 인증 성공 시 일회용 증명 저장
local created = redis.call(
    'SET', KEYS[2], ARGV[2], 'PX', ARGV[3], 'NX'
)

if not created then
    return -3
end

redis.call('DEL', KEYS[1])
return 1