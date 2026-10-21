-- 재전송 대기 중이면 거부
if redis.call('EXISTS', KEYS[2]) == 1 then
    return 0
end

-- 이전 코드가 있어도 새 코드로 교체
redis.call('HSET', KEYS[1],
    'codeHash', ARGV[1],
    'attempts', 0,
    'generation', ARGV[2]
)
redis.call('PEXPIRE', KEYS[1], ARGV[3])
redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[4])

return 1