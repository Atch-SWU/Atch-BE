package com.swu.atch.ai.service;

import com.swu.atch.ai.domain.TimeEstimationProfile;
import com.swu.atch.ai.dto.response.TimeEstimationProfileResponse;
import com.swu.atch.ai.dto.response.TimeEstimationProfileResponse.*;
import com.swu.atch.ai.repository.TimeEstimationProfileRepository;
import com.swu.atch.user.domain.User;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final AiTransactions transactions;
    private final AiHistory history;
    private final ProfileCalculator calculator;
    private final TimeEstimationProfileRepository profiles;

    public TimeEstimationProfileResponse get(Long user, String stamp) {
        return transactions.locked(
                user,
                stamp,
                owner -> {
                    var stored = profiles.findByUser_Id(user);
                    if (stored.isPresent())
                        return response(stored.get(), Source.STORED_SNAPSHOT, null);
                    var state =
                            history.eligible(user, Instant.now()).isEmpty()
                                    ? State.NO_HISTORY
                                    : State.NOT_CALCULATED;
                    return new TimeEstimationProfileResponse(
                            state,
                            Source.STORED_SNAPSHOT,
                            false,
                            1,
                            0,
                            null,
                            0,
                            0,
                            Reliability.LOW,
                            null,
                            null);
                });
    }

    public TimeEstimationProfileResponse recalculate(Long user, String stamp) {
        return transactions.locked(user, stamp, owner -> fresh(owner, Instant.now()));
    }

    // 호출자가 User 잠금을 보유한다. 계산과 저장의 관측 시각을 응답에 명시한다.
    public TimeEstimationProfileResponse fresh(User owner, Instant asOf) {
        var metrics = calculator.calculate(history.eligible(owner.getId(), asOf), asOf);
        var stored = profiles.findByUser_Id(owner.getId());
        if (stored.isEmpty() && metrics.samples() == 0)
            return new TimeEstimationProfileResponse(
                    State.NO_HISTORY,
                    Source.FRESH_COMPUTATION,
                    false,
                    1,
                    0,
                    null,
                    0,
                    0,
                    Reliability.LOW,
                    null,
                    asOf);
        var p =
                stored.orElseGet(
                        () ->
                                TimeEstimationProfile.create(
                                        owner,
                                        metrics.factor(),
                                        metrics.samples(),
                                        metrics.stddev(),
                                        metrics.underestimationRate(),
                                        metrics.score()));
        p.updateMetrics(
                metrics.factor(),
                metrics.samples(),
                metrics.stddev(),
                metrics.underestimationRate(),
                metrics.score());
        profiles.saveAndFlush(p);
        return response(p, Source.FRESH_COMPUTATION, asOf);
    }

    private TimeEstimationProfileResponse response(
            TimeEstimationProfile p, Source source, Instant asOf) {
        var reliability =
                p.getSampleCount() < 2 || p.getProfileReliabilityScore() < 0.35
                        ? Reliability.LOW
                        : p.getProfileReliabilityScore() < 0.65
                                ? Reliability.MEDIUM
                                : Reliability.HIGH;
        return new TimeEstimationProfileResponse(
                p.getSampleCount() == 0 ? State.NO_HISTORY : State.AVAILABLE,
                source,
                true,
                p.getDurationCorrectionFactor(),
                p.getSampleCount(),
                p.getLogRatioStddev(),
                p.getUnderestimationRate(),
                p.getProfileReliabilityScore(),
                reliability,
                p.getUpdatedAt(),
                asOf);
    }
}
