-- SMTP 실패도 미등록 주소와 같은 더미 상태·오입력 횟수·TTL을 유지
if redis.call('HGET', KEYS[1], 'generation') == ARGV[1] then
    return redis.call('HDEL', KEYS[1], 'userId', 'credentialStamp')
end
return 0
