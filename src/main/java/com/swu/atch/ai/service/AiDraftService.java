package com.swu.atch.ai.service;

import com.swu.atch.ai.domain.*;
import com.swu.atch.ai.dto.request.AiDraftRequest;
import com.swu.atch.ai.dto.response.*;
import com.swu.atch.ai.repository.*;
import com.swu.atch.common.exception.*;
import com.swu.atch.todo.domain.TodoStatus;
import com.swu.atch.todo.repository.TodoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiDraftService {
    private final AiTransactions tx;
    private final AiDraftRepository drafts;
    private final AiOperationRepository operations;
    private final TodoRepository todos;
    private final AiRateLimiter limits;

    // 자동 생성한 새 Todo의 통계 제안을 레거시 성공으로 오인하지 않도록 정식 초안을 연결한다.
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void attachGeneratedLocked(com.swu.atch.user.domain.User user, com.swu.atch.todo.domain.Todo todo, Instant now) {
        if (todo.getRecurrenceRuleId() == null || todo.getUserEstimateSource() != com.swu.atch.todo.domain.UserEstimateSource.RECURRENCE_TEMPLATE
                || !todo.getUser().getId().equals(user.getId())) throw bad();
        if (drafts.findByTodo_IdAndUser_Id(todo.getId(), user.getId()).isEmpty())
            drafts.saveAndFlush(new AiDraft(user, todo, null, now));
    }

    public AiDraftResponse issue(Long user, String stamp, AiDraftRequest request) {
        return tx.locked(
                user,
                stamp,
                owner -> {
                    if (request.todoId() != null && request.parentDraftId() != null) throw bad();
                    Instant now = Instant.now();
                    if (request.todoId() != null) {
                        var todo =
                                todos.findByIdAndUser_IdAndDeletedAtIsNull(request.todoId(), user)
                                        .orElseThrow(this::missing);
                        var existing = drafts.findByTodo_IdAndUser_Id(todo.getId(), user);
                        if (existing.isPresent()) {
                            existing.get().renew(now);
                            return response(existing.get(), now);
                        }
                        limits.acquire(user, true, 1);
                        var draft = drafts.saveAndFlush(new AiDraft(owner, todo, null, now));
                        if (todo.getAiEstimatedDurationMinutes() != null) {
                            var legacy = new AiOperation(draft, AiFeature.TIME_PREDICTION);
                            legacy.markLegacy();
                            operations.save(legacy);
                        }
                        return response(draft, now);
                    }
                    AiDraft parent = null;
                    if (request.parentDraftId() != null) {
                        parent = owned(request.parentDraftId(), user);
                        if (parent.getParent() != null
                                || parent.getTodo() != null
                                || parent.getFinalizedAt() != null
                                || !parent.getExpiresAt().isAfter(now)) throw conflict();
                        var decomposition =
                                operations.findByDraft_IdAndFeature(
                                        parent.getId(), AiFeature.DECOMPOSITION);
                        if (decomposition.isEmpty()
                                || decomposition.get().getState() != AiOperationState.SUCCEEDED)
                            throw conflict();
                    }
                    limits.acquire(user, true, 1);
                    return response(
                            drafts.saveAndFlush(new AiDraft(owner, null, parent, now)), now);
                });
    }

    public AiDraft owned(UUID uuid, Long user) {
        var d = drafts.findByDraftIdAndUser_Id(uuid, user).orElseThrow(this::missing);
        if (d.getTodo() != null && d.getTodo().getDeletedAt() != null) throw missing();
        return d;
    }

    public void requireCallable(AiDraft draft, AiFeature feature, Instant now) {
        if (feature == AiFeature.DECOMPOSITION
                && (draft.getTodo() != null || draft.getParent() != null)) throw conflict();
        if (!draft.getExpiresAt().isAfter(now)
                || (draft.getTodo() == null && draft.getFinalizedAt() != null)) throw conflict();
        if (draft.getParent() != null
                && draft.getTodo() == null
                && draft.getParent().getFinalizedAt() != null) throw conflict();
        if (draft.getTodo() != null
                && (draft.getTodo().getStatus() == TodoStatus.COMPLETED
                        || draft.getTodo().getStatus() == TodoStatus.IN_PROGRESS)) throw conflict();
    }

    public AiDraftResponse response(AiDraft draft, Instant now) {
        return new AiDraftResponse(
                draft.getDraftId(),
                draft.getTodo() == null ? null : draft.getTodo().getId(),
                draft.getExpiresAt(),
                draft.getTodo() != null || draft.getParent() != null
                        ? notApplicable()
                        : usage(draft, AiFeature.DECOMPOSITION, now),
                usage(draft, AiFeature.TIME_PREDICTION, now));
    }

    public AiUsageResponse usage(AiDraft draft, AiFeature feature, Instant now) {
        boolean available =
                draft.getExpiresAt().isAfter(now)
                        && (draft.getTodo() != null
                                ? draft.getTodo().getStatus() != TodoStatus.COMPLETED
                                        && draft.getTodo().getStatus() != TodoStatus.IN_PROGRESS
                                : draft.getFinalizedAt() == null
                                        && (draft.getParent() == null
                                                || draft.getParent().getFinalizedAt() == null));
        return operations
                .findByDraft_IdAndFeature(draft.getId(), feature)
                .map(
                        op -> {
                            var state =
                                    op.getState() == AiOperationState.IN_PROGRESS
                                                    && !op.getLeaseUntil().isAfter(now)
                                            ? AiUsageResponse.State.RETRYABLE_FAILURE
                                            : AiUsageResponse.State.valueOf(op.getState().name());
                            boolean retry =
                                    state == AiUsageResponse.State.RETRYABLE_FAILURE && available;
                            return new AiUsageResponse(
                                    state,
                                    retry,
                                    op.getSucceededAt(),
                                    op.getState() == AiOperationState.SUCCEEDED);
                        })
                .orElseGet(
                        () ->
                                new AiUsageResponse(
                                        AiUsageResponse.State.NOT_USED, available, null, false));
    }

    private AiUsageResponse notApplicable() {
        return new AiUsageResponse(AiUsageResponse.State.NOT_APPLICABLE, false, null, false);
    }

    private BusinessException missing() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private BusinessException bad() {
        return new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.RESOURCE_CONFLICT);
    }
}
