package com.swu.atch.ai.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class ProfileCalculator {
    public record Metrics(
            double factor, int samples, Double stddev, double underestimationRate, double score) {}

    public Metrics calculate(List<AiHistory.Observation> records, Instant asOf) {
        if (records.isEmpty()) return new Metrics(1, 0, null, 0, 0);
        double weight = 0, sum = 0;
        int under = 0;
        for (var r : records) {
            double w = weight(r, asOf);
            weight += w;
            sum += w * logRatio(r);
            if (r.actualSeconds() / 60.0 > r.userMinutes()) under++;
        }
        if (weight == 0)
            return new Metrics(1, records.size(), null, (double) under / records.size(), 0);
        double mean = sum / weight, variance = 0;
        for (var r : records) variance += weight(r, asOf) * Math.pow(logRatio(r) - mean, 2);
        Double sigma = records.size() < 2 ? null : Math.sqrt(Math.max(0, variance / weight));
        return new Metrics(
                Math.exp(weight / (weight + 3) * mean),
                records.size(),
                sigma,
                (double) under / records.size(),
                weight / (weight + 3) * Math.exp(sigma == null ? 0 : -sigma));
    }

    private double weight(AiHistory.Observation r, Instant now) {
        return Math.pow(0.5, Duration.between(r.completedAt(), now).toSeconds() / 86400.0 / 30);
    }

    private double logRatio(AiHistory.Observation r) {
        return Math.log(r.actualSeconds() / 60.0) - Math.log(r.userMinutes());
    }

    public static int minutes(double baseline, double adjustment) {
        double result = baseline * adjustment;
        if (!Double.isFinite(result) || result <= 0)
            throw new IllegalArgumentException("예측 시간은 양수 유한수여야 합니다.");
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.round(result)));
    }
}
