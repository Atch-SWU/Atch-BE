package com.swu.atch.ai.repository;

import com.swu.atch.ai.domain.*;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiOperationRepository extends JpaRepository<AiOperation, Long> {
    Optional<AiOperation> findByDraft_IdAndFeature(Long draftId, AiFeature feature);
}
