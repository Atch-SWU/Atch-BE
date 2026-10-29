package com.swu.atch.recurrence.service;

import com.swu.atch.recurrence.repository.RecurrenceRuleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.recurrence.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class RecurrenceGenerationScheduler {
    private static final Logger log = LoggerFactory.getLogger(RecurrenceGenerationScheduler.class);
    private final RecurrenceRuleRepository rules;
    private final RecurrenceGenerationService generation;
    private long afterId;

    @Scheduled(fixedDelayString = "${app.recurrence.scheduler.interval-ms:60000}", initialDelayString = "${app.recurrence.scheduler.interval-ms:60000}")
    public void generateToday() {
        var ids = rules.findGenerationUserIds(afterId);
        if (ids.isEmpty()) { afterId = 0; return; }
        for (Long id : ids) {
            try { generation.prepare(id); }
            catch (RuntimeException ex) {
                // DB 예외 원문·키·접속 정보를 남기지 않는다. 다음 순회에서 재시도한다.
                log.warn("반복 회차 생성 재시도 필요: userId={}, type={}", id, ex.getClass().getSimpleName());
            }
            afterId = id;
        }
    }
}
