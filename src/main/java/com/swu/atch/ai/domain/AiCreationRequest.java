package com.swu.atch.ai.domain;

import com.swu.atch.user.domain.User;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ai_creation_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiCreationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_id", unique = true, updatable = false)
    private AiDraft draft;

    @Column(name = "creation_request_id", nullable = false, updatable = false)
    private UUID creationRequestId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "result_payload",
            nullable = false,
            columnDefinition = "jsonb",
            updatable = false)
    private String resultPayload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AiCreationRequest(
            User user, AiDraft draft, UUID requestId, String hash, String payload) {
        this(user, requestId, hash, payload);
        this.draft = Objects.requireNonNull(draft);
        if (!user.getId().equals(draft.getUser().getId()))
            throw new IllegalArgumentException("생성 요청의 초안 소유자가 올바르지 않습니다.");
    }

    public static AiCreationRequest forOrdinaryTodo(User user, UUID requestId, String hash, String payload) {
        return new AiCreationRequest(user, requestId, hash, payload);
    }

    private AiCreationRequest(User user, UUID requestId, String hash, String payload) {
        this.user = Objects.requireNonNull(user);
        this.creationRequestId = Objects.requireNonNull(requestId);
        if (hash == null || !hash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("생성 요청 해시가 올바르지 않습니다.");
        this.requestHash = hash;
        this.resultPayload = Objects.requireNonNull(payload);
    }
}
