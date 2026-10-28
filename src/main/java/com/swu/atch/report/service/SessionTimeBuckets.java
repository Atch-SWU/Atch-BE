package com.swu.atch.report.service;

import com.swu.atch.session.domain.Session;
import java.math.BigInteger;
import java.time.*;

// 이미 정수 초로 저장된 구간을 누적 비율의 차로 분배하여 경계 반올림 손실을 막는다.
public final class SessionTimeBuckets {
    private SessionTimeBuckets() { }
    public static long seconds(Session session, Instant from, Instant to, Instant asOf) {
        Instant start = session.getStartedAt();
        Instant end = session.getEndedAt() == null ? asOf : session.getEndedAt();
        Instant left = start.isAfter(from) ? start : from;
        Instant right = end.isBefore(to) ? end : to;
        if (right.isAfter(asOf)) right = asOf;
        if (!end.isAfter(start) || !right.isAfter(left)) return 0;
        long total = session.getEndedAt() == null ? Duration.between(start, end).getSeconds() : session.getDurationSeconds();
        if (total == 0) return 0;
        BigInteger span = micros(start, end);
        return allocated(start, right, span, total) - allocated(start, left, span, total);
    }
    private static long allocated(Instant start, Instant point, BigInteger span, long total) {
        return micros(start, point).multiply(BigInteger.valueOf(total)).divide(span).longValueExact();
    }
    private static BigInteger micros(Instant from, Instant to) {
        Duration d = Duration.between(from, to);
        return BigInteger.valueOf(d.getSeconds()).multiply(BigInteger.valueOf(1_000_000))
                .add(BigInteger.valueOf(d.getNano() / 1_000));
    }
}
