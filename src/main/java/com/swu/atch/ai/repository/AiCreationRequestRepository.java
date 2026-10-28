package com.swu.atch.ai.repository;

import com.swu.atch.ai.domain.AiCreationRequest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiCreationRequestRepository extends JpaRepository<AiCreationRequest, Long> {
    Optional<AiCreationRequest> findByUser_IdAndCreationRequestId(Long userId, UUID requestId);

    boolean existsByDraft_Id(Long draftId);
}
