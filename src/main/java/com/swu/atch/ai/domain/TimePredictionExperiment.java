
package com.swu.atch.ai.domain;

import com.swu.atch.todo.domain.Todo;
import com.swu.atch.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "time_prediction_experiments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimePredictionExperiment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prediction_run_id", nullable = false)
    private UUID predictionRunId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "todo_id", nullable = false)
    private Todo todo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_type", nullable = false, length = 40)
    private PredictionStrategy strategyType;

    @Column(name = "predicted_minutes", nullable = false)
    private Integer predictedMinutes;

    @Column(name = "actual_minutes")
    private Integer actualMinutes;

    @Column(name = "absolute_error_minutes")
    private Integer absoluteErrorMinutes;

    @Column(name = "absolute_percentage_error")
    private Double absolutePercentageError;

    @Column(name = "baseline_prediction_minutes")
    private Integer baselinePredictionMinutes;

    @Column(name = "duration_correction_factor")
    private Double durationCorrectionFactor;

    @Column(name = "simple_factor")
    private Double simpleFactor;

    @Column(name = "profile_reliability_score")
    private Double profileReliabilityScore;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "log_ratio_stddev")
    private Double logRatioStddev;

    @Column(name = "underestimation_rate")
    private Double underestimationRate;

    @Column(name = "context_adjustment_factor")
    private Double contextAdjustmentFactor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "similar_todo_ids", columnDefinition = "jsonb")
    private List<Long> similarTodoIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_codes", columnDefinition = "jsonb")
    private List<PredictionReasonCode> reasonCodes;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    public static TimePredictionExperiment create(
            UUID predictionRunId,
            Todo todo,
            PredictionStrategy strategyType,
            int predictedMinutes
    ) {
        Objects.requireNonNull(predictionRunId, "예측 실행 ID는 필수입니다.");
        Objects.requireNonNull(todo, "Todo는 필수입니다.");
        Objects.requireNonNull(strategyType, "예측 전략은 필수입니다.");

        if (todo.getId() == null) {
            throw new IllegalArgumentException("저장된 Todo만 사용할 수 있습니다.");
        }

        if (predictedMinutes <= 0) {
            throw new IllegalArgumentException("예측시간은 0보다 커야 합니다.");
        }

        TimePredictionExperiment experiment =
                new TimePredictionExperiment();

        experiment.predictionRunId = predictionRunId;
        experiment.todo = todo;
        experiment.user = Objects.requireNonNull(
                todo.getUser(), "Todo의 소유자는 필수입니다."
        );
        experiment.strategyType = strategyType;
        experiment.predictedMinutes = predictedMinutes;

        return experiment;
    }

    // 예측 당시의 통계 및 전략별 보정값 저장
    public void recordProfileSnapshot(
            Integer baselinePredictionMinutes,
            Double durationCorrectionFactor,
            Double simpleFactor,
            Double profileReliabilityScore,
            Integer sampleCount,
            Double logRatioStddev,
            Double underestimationRate
    ) {
        this.baselinePredictionMinutes = baselinePredictionMinutes;
        this.durationCorrectionFactor = durationCorrectionFactor;
        this.simpleFactor = simpleFactor;
        this.profileReliabilityScore = profileReliabilityScore;
        this.sampleCount = sampleCount;
        this.logRatioStddev = logRatioStddev;
        this.underestimationRate = underestimationRate;
    }

    // 예측 당시 사용한 AI 맥락과 모델 정보 저장
    public void recordContextSnapshot(
            Double contextAdjustmentFactor,
            List<Long> similarTodoIds,
            List<PredictionReasonCode> reasonCodes,
            String modelName,
            String promptVersion,
            String embeddingModel
    ) {
        this.contextAdjustmentFactor = contextAdjustmentFactor;

        this.similarTodoIds = similarTodoIds == null
                ? null : List.copyOf(similarTodoIds);

        this.reasonCodes = reasonCodes == null
                ? null : List.copyOf(reasonCodes);

        this.modelName = modelName;
        this.promptVersion = promptVersion;
        this.embeddingModel = embeddingModel;
    }

    // Todo 완료 후 실제시간과 예측 오차 기록
    public void recordEvaluation(
            int actualMinutes,
            int absoluteErrorMinutes,
            Double absolutePercentageError,
            Instant evaluatedAt
    ) {
        Objects.requireNonNull(evaluatedAt, "평가 시각은 필수입니다.");

        if (actualMinutes < 0 || absoluteErrorMinutes < 0) {
            throw new IllegalArgumentException(
                    "실제시간과 절대 오차는 음수일 수 없습니다."
            );
        }

        if (absolutePercentageError != null
                && (!Double.isFinite(absolutePercentageError)
                || absolutePercentageError < 0)) {
            throw new IllegalArgumentException(
                    "백분율 오차는 0 이상의 유한한 값이어야 합니다."
            );
        }

        this.actualMinutes = actualMinutes;
        this.absoluteErrorMinutes = absoluteErrorMinutes;
        this.absolutePercentageError = absolutePercentageError;
        this.evaluatedAt = evaluatedAt;
    }
}
