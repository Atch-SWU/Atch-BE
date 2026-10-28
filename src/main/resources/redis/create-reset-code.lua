if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
-- 재발급 시 이전 세대의 계정 연결을 남기지 않음
redis.call('HDEL', KEYS[1], 'userId', 'credentialStamp')
redis.call('HSET', KEYS[1], 'codeHash', ARGV[1], 'attempts', 0, 'generation', ARGV[2])
redis.call('PEXPIRE', KEYS[1], ARGV[3])
redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[4])
return 1
