package com.swu.atch.ai.domain;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ai_operations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"draft_id", "feature"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false, updatable = false)
    private AiDraft draft;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiFeature feature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiOperationState state;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "input_hash", length = 64)
    private String inputHash;

    @Column(name = "attempt_id")
    private UUID attemptId;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "succeeded_at")
    private Instant succeededAt;

    @Column(name = "as_of")
    private Instant asOf;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", columnDefinition = "jsonb")
    private String resultPayload;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "error_category", length = 50)
    private String errorCategory;

    public AiOperation(AiDraft draft, AiFeature feature) {
        this.draft = java.util.Objects.requireNonNull(draft);
        this.feature = java.util.Objects.requireNonNull(feature);
        this.state = AiOperationState.RETRYABLE_FAILURE;
    }

    public void markLegacy() {
        if (state != AiOperationState.RETRYABLE_FAILURE || attemptCount != 0)
            throw new IllegalStateException("기존 성공 이력을 레거시 상태로 바꿀 수 없습니다.");
        state = AiOperationState.LEGACY_UNKNOWN;
    }

    public UUID reserve(String hash, Instant now) {
        if (hash == null || !hash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("입력 해시 형식 오류");
        java.util.Objects.requireNonNull(now);
        if (state == AiOperationState.IN_PROGRESS && leaseUntil.isAfter(now))
            throw new IllegalStateException("아직 유효한 실행 예약입니다.");
        if (state == AiOperationState.SUCCEEDED || state == AiOperationState.LEGACY_UNKNOWN)
            throw new IllegalStateException("이미 소비된 AI 기능입니다.");
        inputHash = hash;
        attemptId = UUID.randomUUID();
        leaseUntil = now.plusSeconds(120);
        attemptCount++;
        state = AiOperationState.IN_PROGRESS;
        errorCategory = null;
        return attemptId;
    }

    // 예약 세대가 바뀌면 늦게 도착한 외부 응답은 새 결과를 덮어쓸 수 없다.
    public boolean owns(UUID attempt) {
        return state == AiOperationState.IN_PROGRESS && attemptId.equals(attempt);
    }

    public void succeed(
            UUID attempt, String payload, Instant asOf, Instant now, String model, String version) {
        if (!owns(attempt)) throw new IllegalStateException("만료된 AI 시도입니다.");
        state = AiOperationState.SUCCEEDED;
        resultPayload = payload;
        this.asOf = asOf;
        succeededAt = now;
        modelName = model;
        promptVersion = version;
        leaseUntil = null;
    }

    public void fail(UUID attempt, String category) {
        if (!owns(attempt)) return;
        state = AiOperationState.RETRYABLE_FAILURE;
        errorCategory = category;
        leaseUntil = null;
    }
}
