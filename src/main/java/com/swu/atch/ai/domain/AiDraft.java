package com.swu.atch.ai.domain;

import com.swu.atch.todo.domain.Todo;
import com.swu.atch.user.domain.User;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ai_drafts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "draft_id", nullable = false, unique = true, updatable = false)
    private UUID draftId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_id", unique = true)
    private Todo todo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", updatable = false)
    private AiDraft parent;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AiDraft(User user, Todo todo, AiDraft parent, Instant now) {
        this.user = Objects.requireNonNull(user);
        if (parent != null && !parent.getUser().getId().equals(user.getId()))
            throw new IllegalArgumentException("초안 소유자가 다릅니다.");
        if (todo != null && !todo.getUser().getId().equals(user.getId()))
            throw new IllegalArgumentException("Todo 소유자가 다릅니다.");
        this.draftId = UUID.randomUUID();
        this.todo = todo;
        this.parent = parent;
        renew(now);
    }

    // 작업 입력의 유효기간과 영속 성공 이력의 수명은 독립이다.
    public void renew(Instant now) {
        expiresAt = Objects.requireNonNull(now).plusSeconds(86400);
    }

    public void finalizeDraft(Instant now) {
        if (finalizedAt == null) finalizedAt = Objects.requireNonNull(now);
    }

    public void attach(Todo target, Instant now) {
        if (!target.getUser().getId().equals(user.getId())
                || (todo != null && !todo.getId().equals(target.getId())))
            throw new IllegalArgumentException("초안은 한 소유자의 한 Todo에만 연결합니다.");
        todo = target;
        finalizeDraft(now);
    }
}
