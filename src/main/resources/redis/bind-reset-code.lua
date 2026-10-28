if redis.call('HGET', KEYS[1], 'generation') ~= ARGV[1] then return 0 end
redis.call('HSET', KEYS[1], 'userId', ARGV[2], 'credentialStamp', ARGV[3])
return 1
