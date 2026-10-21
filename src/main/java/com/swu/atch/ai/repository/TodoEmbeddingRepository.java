package com.swu.atch.ai.repository;

import com.swu.atch.ai.domain.TodoEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TodoEmbeddingRepository
        extends JpaRepository<TodoEmbedding, Long> {

    Optional<TodoEmbedding> findByTodo_IdAndTodo_User_Id(
            Long todoId,
            Long userId
    );
}