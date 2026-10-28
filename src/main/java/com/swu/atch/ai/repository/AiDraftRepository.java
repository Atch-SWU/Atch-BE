package com.swu.atch.ai.repository;

import com.swu.atch.ai.domain.AiDraft;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiDraftRepository extends JpaRepository<AiDraft, Long> {
    Optional<AiDraft> findByDraftIdAndUser_Id(UUID draftId, Long userId);

    Optional<AiDraft> findByTodo_IdAndUser_Id(Long todoId, Long userId);
}
