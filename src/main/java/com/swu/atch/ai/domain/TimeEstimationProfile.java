
package com.swu.atch.ai.domain;

import com.swu.atch.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "time_estimation_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_time_estimation_profiles_user",
                columnNames = "user_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeEstimationProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "duration_correction_factor", nullable = false)
    private double durationCorrectionFactor;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "log_ratio_stddev")
    private Double logRatioStddev;

    @Column(name = "underestimation_rate", nullable = false)
    private double underestimationRate;

    @Column(name = "profile_reliability_score", nullable = false)
    private double profileReliabilityScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TimeEstimationProfile create(
            User user,
            double durationCorrectionFactor,
            int sampleCount,
            Double logRatioStddev,
            double underestimationRate,
            double profileReliabilityScore
    ) {
        Objects.requireNonNull(user, "User는 필수입니다.");

        TimeEstimationProfile profile = new TimeEstimationProfile();
        profile.user = user;

        profile.updateMetrics(
                durationCorrectionFactor,
                sampleCount,
                logRatioStddev,
                underestimationRate,
                profileReliabilityScore
        );

        return profile;
    }

    public void updateMetrics(
            double durationCorrectionFactor,
            int sampleCount,
            Double logRatioStddev,
            double underestimationRate,
            double profileReliabilityScore
    ) {
        if (!Double.isFinite(durationCorrectionFactor)
                || durationCorrectionFactor <= 0) {
            throw new IllegalArgumentException(
                    "시간 보정 배수는 0보다 큰 유한한 값이어야 합니다."
            );
        }

        if (sampleCount < 0) {
            throw new IllegalArgumentException(
                    "표본 수는 음수일 수 없습니다."
            );
        }

        if (logRatioStddev != null &&
                (!Double.isFinite(logRatioStddev)
                        || logRatioStddev < 0)) {
            throw new IllegalArgumentException(
                    "로그 표준편차는 0 이상의 유한한 값이어야 합니다."
            );
        }

        validateRate(underestimationRate, "과소예측 비율");
        validateRate(profileReliabilityScore, "프로필 신뢰도");

        this.durationCorrectionFactor = durationCorrectionFactor;
        this.sampleCount = sampleCount;
        this.logRatioStddev = logRatioStddev;
        this.underestimationRate = underestimationRate;
        this.profileReliabilityScore = profileReliabilityScore;
    }

    private static void validateRate(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(
                    name + "은 0 이상 1 이하의 유한한 값이어야 합니다."
            );
        }
    }
}
