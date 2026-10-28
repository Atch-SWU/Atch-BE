package com.swu.atch.ai.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class ProfileCalculatorTest {
    private final ProfileCalculator calculator = new ProfileCalculator();
    private final Instant now = Instant.parse("2026-10-29T00:00:00Z");

    private AiHistory.Observation row(int minutes, long seconds, long age) {
        return new AiHistory.Observation(
                1, "작업", null, null, minutes, seconds, now.minusSeconds(age));
    }

    @Test
    void noHistoryUsesPrior() {
        var m = calculator.calculate(List.of(), now);
        assertThat(m.factor()).isEqualTo(1);
        assertThat(m.samples()).isZero();
        assertThat(m.stddev()).isNull();
    }

    @Test
    void oneSampleUsesShrinkageAndNoInventedVariance() {
        var m = calculator.calculate(List.of(row(10, 1200, 0)), now);
        assertThat(m.factor()).isCloseTo(Math.pow(2, 0.25), within(1e-12));
        assertThat(m.stddev()).isNull();
        assertThat(m.score()).isEqualTo(0.25);
    }

    @Test
    void recentRecordsHaveGreaterWeight() {
        var m = calculator.calculate(List.of(row(10, 1200, 0), row(10, 300, 30 * 86400)), now);
        double mean = (Math.log(2) + 0.5 * Math.log(0.5)) / 1.5;
        assertThat(m.factor()).isCloseTo(Math.exp(1.5 / 4.5 * mean), within(1e-12));
        assertThat(m.underestimationRate()).isEqualTo(0.5);
    }

    @Test
    void preservesFractionalMinutes() {
        assertThat(calculator.calculate(List.of(row(1, 1, 0)), now).factor())
                .isCloseTo(Math.pow(1.0 / 60, 0.25), within(1e-12));
    }

    @Test
    void handlesNumericallyVanishedWeights() {
        var m = calculator.calculate(List.of(row(10, 600, 100000L * 86400)), now);
        assertThat(m.factor()).isEqualTo(1);
        assertThat(m.score()).isZero();
        assertThat(m.samples()).isEqualTo(1);
    }

    @Test
    void roundsOnceAndClampsFinalMinutes() {
        assertThat(ProfileCalculator.minutes(10.4, 1.1)).isEqualTo(11);
        assertThat(ProfileCalculator.minutes(0.1, 0.75)).isEqualTo(1);
        assertThat(ProfileCalculator.minutes(Integer.MAX_VALUE * 100.0, 1.4))
                .isEqualTo(Integer.MAX_VALUE);
        assertThatThrownBy(() -> ProfileCalculator.minutes(Double.NaN, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
