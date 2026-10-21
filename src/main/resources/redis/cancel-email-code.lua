-- 메일 발송에 실패한 경우 이번 요청이 생성한 코드만 정리
if redis.call('HGET', KEYS[1], 'generation') ~= ARGV[1] then
    return 0
end

redis.call('DEL', KEYS[1])

if redis.call('GET', KEYS[2]) == ARGV[1] then
    redis.call('DEL', KEYS[2])
end

return 1