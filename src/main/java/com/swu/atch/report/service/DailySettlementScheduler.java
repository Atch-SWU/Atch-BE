package com.swu.atch.report.service;

import com.swu.atch.todo.service.TodoSchedulePolicy;
import com.swu.atch.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

// 기동 후·자정 후 누락분을 같은 경로로 처리한다. 공개 정산 쓰기 API는 없다.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.report.settlement.enabled", havingValue = "true", matchIfMissing = true)
public class DailySettlementScheduler {
    private static final Logger log = LoggerFactory.getLogger(DailySettlementScheduler.class);
    private final UserRepository users;
    private final DailySettlementService settlements;
    private final TodoSchedulePolicy clock;
    private long afterId;

    @Scheduled(fixedDelayString = "${app.report.settlement.interval-ms:60000}", initialDelayString = "${app.report.settlement.interval-ms:60000}")
    public void settlePending() {
        var ids = users.findDailyMaintenanceCandidates(afterId, clock.seoulDate(clock.now()));
        if (ids.isEmpty()) { afterId = 0; return; }
        for (Long id : ids) {
            try { settlements.prepare(id, null); }
            catch (RuntimeException ex) {
                // JDBC 오류 원문에는 연결 정보가 들어갈 수 있으므로 출력하지 않는다.
                log.warn("일일 확정 재시도 필요: userId={}, type={}", id, ex.getClass().getSimpleName());
            }
            afterId = id;
        }
    }
}
