package com.swu.atch.ai.domain;

import com.swu.atch.todo.domain.Todo;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

@Entity
@Table(name = "todo_embeddings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoEmbedding {

    public static final int EMBEDDING_DIMENSION = 1024;

    @Id
    @Column(name = "todo_id")
    private Long todoId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "todo_id", nullable = false)
    private Todo todo;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIMENSION)
    @Column(name = "embedding", nullable = false)
    private float[] embedding;

    @Column(name = "embedding_model", nullable = false, length = 100)
    private String embeddingModel;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "source_text_hash", nullable = false, length = 64)
    private String sourceTextHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TodoEmbedding create(
            Todo todo,
            float[] embedding,
            String embeddingModel,
            String sourceTextHash
    ) {
        Objects.requireNonNull(todo, "Todo는 필수입니다.");

        TodoEmbedding todoEmbedding = new TodoEmbedding();
        todoEmbedding.todo = todo;
        todoEmbedding.replaceEmbedding(
                embedding, embeddingModel, sourceTextHash
        );

        return todoEmbedding;
    }

    public void replaceEmbedding(
            float[] embedding,
            String embeddingModel,
            String sourceTextHash
    ) {
        Objects.requireNonNull(embedding, "임베딩은 필수입니다.");

        if (embedding.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException(
                    "임베딩은 1024차원이어야 합니다."
            );
        }

        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(
                        "임베딩에는 유한한 숫자만 사용할 수 있습니다."
                );
            }
        }

        if (embeddingModel == null
                || embeddingModel.isBlank()
                || embeddingModel.length() > 100) {
            throw new IllegalArgumentException(
                    "임베딩 모델명은 1~100자여야 합니다."
            );
        }

        if (sourceTextHash == null
                || !sourceTextHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(
                    "원본문 해시는 64자리 SHA-256 16진수여야 합니다."
            );
        }

        this.embedding = Arrays.copyOf(
                embedding, embedding.length
        );
        this.embeddingModel = embeddingModel;
        this.sourceTextHash = sourceTextHash;
    }


    public float[] getEmbedding() {
        return embedding == null
                ? null
                : Arrays.copyOf(embedding, embedding.length);
    }
}
