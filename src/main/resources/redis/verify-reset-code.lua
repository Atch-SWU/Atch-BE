local stored = redis.call('HGET', KEYS[1], 'codeHash')
if not stored then return 0 end
local userId = redis.call('HGET', KEYS[1], 'userId')
local stamp = redis.call('HGET', KEYS[1], 'credentialStamp')
if stored ~= ARGV[1] or not userId or not stamp then
    local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
    if attempts >= 5 then redis.call('DEL', KEYS[1]); return -2 end
    return -1
end
local proof = ARGV[2] .. ':' .. userId .. ':' .. stamp
if not redis.call('SET', KEYS[2], proof, 'PX', ARGV[3], 'NX') then return -3 end
redis.call('DEL', KEYS[1])
return 1
