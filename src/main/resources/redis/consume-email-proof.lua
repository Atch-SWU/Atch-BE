
-- 인증 증명에 연결된 이메일 조회
local verifiedEmail = redis.call('GET', KEYS[1])

if not verifiedEmail then
    return 0
end

-- 검증된 이메일과 회원가입 이메일이 다르면 거부
if verifiedEmail ~= ARGV[1] then
    return -1
end

-- 이메일 일치 시 인증 증명을 원자적으로 소비
redis.call('DEL', KEYS[1])

return 1
