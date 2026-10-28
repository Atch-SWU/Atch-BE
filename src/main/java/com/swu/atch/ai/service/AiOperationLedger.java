package com.swu.atch.ai.service;

import com.swu.atch.ai.domain.*;
import com.swu.atch.ai.repository.AiOperationRepository;
import com.swu.atch.common.exception.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiOperationLedger {
    private final AiOperationRepository operations;
    private final AiDraftService drafts;

    public record Ticket(
            UUID draftId, UUID attemptId, String hash, String cachedPayload, Instant asOf) {
        public boolean cached() {
            return cachedPayload != null;
        }
    }

    // 먼저 전체 입력을 검사한 뒤 예약한다. 묶음의 뒤쪽 충돌로 앞쪽 외부 호출이 발생하지 않는다.
    public void check(AiDraft draft, AiFeature feature, String hash, Instant now) {
        var stored = operations.findByDraft_IdAndFeature(draft.getId(), feature);
        if (stored.isPresent()) {
            var op = stored.get();
            if (op.getState() == AiOperationState.LEGACY_UNKNOWN)
                throw new BusinessException(ErrorCode.AI_ALREADY_USED);
            if (op.getState() == AiOperationState.SUCCEEDED) {
                if (!hash.equals(op.getInputHash()))
                    throw new BusinessException(ErrorCode.AI_ALREADY_USED);
                return;
            }
            if (op.getState() == AiOperationState.IN_PROGRESS && op.getLeaseUntil().isAfter(now))
                throw new BusinessException(ErrorCode.AI_IN_PROGRESS);
        }
        drafts.requireCallable(draft, feature, now);
    }

    public boolean cached(AiDraft draft, AiFeature feature) {
        return operations
                .findByDraft_IdAndFeature(draft.getId(), feature)
                .map(o -> o.getState() == AiOperationState.SUCCEEDED)
                .orElse(false);
    }

    public Ticket reserve(AiDraft draft, AiFeature feature, String hash, Instant now) {
        check(draft, feature, hash, now);
        var op =
                operations
                        .findByDraft_IdAndFeature(draft.getId(), feature)
                        .orElseGet(() -> new AiOperation(draft, feature));
        if (op.getState() == AiOperationState.SUCCEEDED)
            return new Ticket(draft.getDraftId(), null, hash, op.getResultPayload(), op.getAsOf());
        UUID attempt = op.reserve(hash, now);
        operations.saveAndFlush(op);
        return new Ticket(draft.getDraftId(), attempt, hash, null, now);
    }

    public void succeed(Long user, Ticket ticket, AiFeature feature, String payload, String model) {
        var op = operation(user, ticket, feature);
        if (!op.owns(ticket.attemptId())) throw new BusinessException(ErrorCode.RESOURCE_CONFLICT);
        op.succeed(ticket.attemptId(), payload, ticket.asOf(), Instant.now(), model, "ai-v1");
    }

    public void fail(Long user, Ticket ticket, AiFeature feature, String category) {
        if (!ticket.cached()) operation(user, ticket, feature).fail(ticket.attemptId(), category);
    }

    private AiOperation operation(Long user, Ticket ticket, AiFeature feature) {
        var draft = drafts.owned(ticket.draftId(), user);
        return operations
                .findByDraft_IdAndFeature(draft.getId(), feature)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_CONFLICT));
    }
}
