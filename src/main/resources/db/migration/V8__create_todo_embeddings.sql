
-- Todo 의미 벡터
CREATE TABLE todo_embeddings (
    todo_id BIGINT PRIMARY KEY,
    embedding VECTOR(1024) NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    source_text_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_todo_embeddings_todo
        FOREIGN KEY (todo_id) REFERENCES todos(id),

    CONSTRAINT ck_todo_embeddings_model
        CHECK (char_length(btrim(embedding_model)) > 0),

    CONSTRAINT ck_todo_embeddings_hash
        CHECK (source_text_hash ~ '^[0-9a-fA-F]{64}$')
);

