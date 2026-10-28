local value = redis.call('GET', KEYS[1])
if not value or string.sub(value, 1, string.len(ARGV[1]) + 1) ~= ARGV[1] .. ':' then return nil end
redis.call('DEL', KEYS[1])
return value
