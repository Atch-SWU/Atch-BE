
-- 세션 키 중복 여부 확인
if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end

-- Refresh Session 생성
redis.call('HSET', KEYS[1],
    'userId', ARGV[1],
    'deviceId', ARGV[2],
    'tokenHash', ARGV[3],
    'issuedAt', ARGV[4],
    'lastRotatedAt', ARGV[4],
    'absoluteExpiresAt', ARGV[5],
    'credentialStamp', ARGV[6]
)

-- 최초 발급 시점 기준 절대 만료시간 지정
redis.call('PEXPIREAT', KEYS[1], ARGV[5])

-- 사용자별 전체 세션 정리용 목록. 세션과 함께 원자적으로 등록
redis.call('SADD', KEYS[2], ARGV[7])
redis.call('PEXPIRE', KEYS[2], ARGV[8])

return 1
