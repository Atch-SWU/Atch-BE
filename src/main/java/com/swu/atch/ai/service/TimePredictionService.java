package com.swu.atch.ai.service;

import com.swu.atch.ai.client.*;
import com.swu.atch.ai.domain.*;
import com.swu.atch.ai.dto.request.*;
import com.swu.atch.ai.dto.response.*;
import com.swu.atch.ai.dto.response.TimePredictionResult.*;
import com.swu.atch.common.exception.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TimePredictionService {
    private final AiTransactions tx;
    private final AiDraftService drafts;
    private final AiOperationLedger ledger;
    private final AiRateLimiter limits;
    private final ProfileService profiles;
    private final SimilarTodoService similar;
    private final ClaudeGateway claude;
    private final PredictionTokenService tokens;
    private final AiJson json;

    public record StoredPrediction(TimePredictionResult prediction, String todoVersion) {}

    private record Work(
            TimePredictionRequest input,
            AiOperationLedger.Ticket ticket,
            String todoVersion,
            Long todoId) {}

    private record Prepared(List<Work> work, TimeEstimationProfileResponse profile, Instant asOf) {}

    private record Completed(
            List<TimePredictionResult> predictions,
            List<PredictionTokenService.Offer> offers,
            TimeEstimationProfileResponse profile,
            Instant asOf) {}

    public TimePredictionResponse predict(Long user, String stamp, TimePredictionRequest request) {
        var completed = run(user, stamp, null, List.of(request));
        var issued = tokens.issue(user, stamp, request.draftId(), completed.offers());
        var prediction = completed.predictions().getFirst();
        return new TimePredictionResponse(
                prediction,
                prediction.profile(),
                issued.token(),
                issued.expiresAt(),
                prediction.asOf());
    }

    public TimePredictionBatchResponse batch(
            Long user, String stamp, TimePredictionBatchRequest request) {
        var inputs =
                request.tasks().stream()
                        .map(
                                t ->
                                        new TimePredictionRequest(
                                                t.draftId(),
                                                t.title(),
                                                request.sourceTaskName(),
                                                request.memo(),
                                                t.userEstimatedDurationMinutes()))
                        .toList();
        var completed = run(user, stamp, request.decompositionDraftId(), inputs);
        var issued = tokens.issue(user, stamp, request.decompositionDraftId(), completed.offers());
        long userTotal =
                completed.predictions().stream()
                        .mapToLong(TimePredictionResult::userEstimatedDurationMinutes)
                        .sum();
        long aiTotal =
                completed.predictions().stream()
                        .mapToLong(TimePredictionResult::aiEstimatedDurationMinutes)
                        .sum();
        return new TimePredictionBatchResponse(
                request.decompositionDraftId(),
                completed.predictions(),
                completed.profile(),
                userTotal,
                aiTotal,
                issued.token(),
                issued.expiresAt(),
                completed.asOf());
    }

    private Completed run(
            Long user, String stamp, UUID parent, List<TimePredictionRequest> inputs) {
        Prepared prepared =
                tx.locked(
                        user,
                        stamp,
                        owner -> {
                            Instant now = Instant.now();
                            var ids = new HashSet<UUID>();
                            var work = new ArrayList<Work>();
                            var root = parent == null ? null : drafts.owned(parent, user);
                            if (root != null
                                    && (root.getTodo() != null
                                            || root.getParent() != null
                                            || root.getFinalizedAt() != null)) throw conflict();
                            int fresh = 0;
                            for (var input : inputs) {
                                if (!ids.add(input.draftId()))
                                    throw new BusinessException(ErrorCode.INVALID_REQUEST);
                                var draft = drafts.owned(input.draftId(), user);
                                if (root != null
                                        && (draft.getParent() == null
                                                || !draft.getParent().getId().equals(root.getId())))
                                    throw conflict();
                                if (root == null
                                        && draft.getParent() != null
                                        && draft.getTodo() == null) throw conflict();
                                if (draft.getTodo() != null
                                        && !Objects.equals(
                                                AiJson.text(draft.getTodo().getSourceTaskName()),
                                                AiJson.text(input.sourceTaskName())))
                                    throw conflict();
                                String hash =
                                        PredictionInputs.hash(
                                                json,
                                                input.title(),
                                                input.sourceTaskName(),
                                                input.memo(),
                                                input.userEstimatedDurationMinutes());
                                ledger.check(draft, AiFeature.TIME_PREDICTION, hash, now);
                                if (!ledger.cached(draft, AiFeature.TIME_PREDICTION)) fresh++;
                            }
                            if (fresh > 0) limits.acquire(user, false, fresh);
                            var profile = profiles.fresh(owner, now);
                            for (var input : inputs) {
                                var draft = drafts.owned(input.draftId(), user);
                                String hash =
                                        PredictionInputs.hash(
                                                json,
                                                input.title(),
                                                input.sourceTaskName(),
                                                input.memo(),
                                                input.userEstimatedDurationMinutes());
                                var ticket =
                                        ledger.reserve(draft, AiFeature.TIME_PREDICTION, hash, now);
                                work.add(
                                        new Work(
                                                input,
                                                ticket,
                                                draft.getTodo() == null
                                                        ? ""
                                                        : draft.getTodo().getUpdatedAt().toString(),
                                                draft.getTodo() == null
                                                        ? null
                                                        : draft.getTodo().getId()));
                            }
                            return new Prepared(List.copyOf(work), profile, now);
                        });
        try {
            var fresh = prepared.work().stream().filter(w -> !w.ticket().cached()).toList();
            var searches =
                    fresh.isEmpty()
                            ? List.<SimilarTodoSearchResponse>of()
                            : similar.batch(
                                    user,
                                    stamp,
                                    fresh.stream()
                                            .map(
                                                    w ->
                                                            new SimilarTodoService.SearchInput(
                                                                    w.input().title(),
                                                                    w.input().sourceTaskName(),
                                                                    w.input().memo(),
                                                                    w.todoId()))
                                            .toList(),
                                    prepared.asOf());
            var contexts = new ArrayList<ClaudeGateway.Context>();
            for (int i = 0; i < fresh.size(); i++) {
                var input = fresh.get(i).input();
                contexts.add(
                        new ClaudeGateway.Context(
                                input.draftId(),
                                AiJson.title(input.title()),
                                input.sourceTaskName(),
                                input.memo(),
                                input.userEstimatedDurationMinutes(),
                                input.userEstimatedDurationMinutes()
                                        * prepared.profile().durationCorrectionFactor(),
                                prepared.profile(),
                                searches.get(i).items()));
            }
            Map<UUID, ClaudeGateway.Advice> advice = Map.of();
            ClaudeStatus status = ClaudeStatus.APPLIED;
            if (!fresh.isEmpty()) {
                try {
                    if (!claude.enabled()) throw new AiProviderException(ClaudeStatus.DISABLED);
                    advice = claude.predict(contexts);
                } catch (AiProviderException ex) {
                    status = ex.status();
                }
            }
            var results = new LinkedHashMap<UUID, StoredPrediction>();
            for (var work : prepared.work()) {
                if (work.ticket().cached()) {
                    var old = json.read(work.ticket().cachedPayload(), StoredPrediction.class);
                    results.put(
                            work.input().draftId(),
                            new StoredPrediction(cached(old.prediction()), old.todoVersion()));
                    continue;
                }
                int i = fresh.indexOf(work);
                var input = work.input();
                var a = advice.get(input.draftId());
                boolean success = status == ClaudeStatus.APPLIED && valid(a);
                double adjustment = success ? a.factor() : 1;
                ClaudeStatus itemStatus =
                        success
                                ? ClaudeStatus.APPLIED
                                : status == ClaudeStatus.APPLIED
                                        ? ClaudeStatus.INVALID_RESPONSE
                                        : status;
                double baseline =
                        input.userEstimatedDurationMinutes()
                                * prepared.profile().durationCorrectionFactor();
                var usage =
                        new AiUsageResponse(
                                success
                                        ? AiUsageResponse.State.SUCCEEDED
                                        : AiUsageResponse.State.RETRYABLE_FAILURE,
                                !success,
                                success ? Instant.now() : null,
                                success);
                var codes =
                        success ? a.reasonCodes() : List.of(PredictionReasonCode.BASELINE_RETAINED);
                var result =
                        new TimePredictionResult(
                                input.draftId(),
                                work.todoId(),
                                input.userEstimatedDurationMinutes(),
                                prepared.profile().durationCorrectionFactor(),
                                baseline,
                                ProfileCalculator.minutes(baseline, adjustment),
                                adjustment,
                                success ? Mode.HYBRID : Mode.STATISTICAL_BASELINE,
                                itemStatus,
                                searches.get(i).voyageStatus(),
                                codes,
                                searches.get(i).items(),
                                usage,
                                false,
                                prepared.asOf(),
                                prepared.profile());
                results.put(input.draftId(), new StoredPrediction(result, work.todoVersion()));
            }
            tx.locked(
                    user,
                    stamp,
                    owner -> {
                        for (var work : fresh) {
                            var value = results.get(work.input().draftId());
                            if (value.prediction().claudeStatus() == ClaudeStatus.APPLIED)
                                ledger.succeed(
                                        user,
                                        work.ticket(),
                                        AiFeature.TIME_PREDICTION,
                                        json.write(value),
                                        claude.model());
                            else
                                ledger.fail(
                                        user,
                                        work.ticket(),
                                        AiFeature.TIME_PREDICTION,
                                        value.prediction().claudeStatus().name());
                        }
                        return null;
                    });
            var offers =
                    prepared.work().stream()
                            .map(
                                    w -> {
                                        var value = results.get(w.input().draftId());
                                        return new PredictionTokenService.Offer(
                                                w.input().draftId(),
                                                w.ticket().hash(),
                                                value.prediction().aiEstimatedDurationMinutes(),
                                                w.todoVersion());
                                    })
                            .toList();
            return new Completed(
                    results.values().stream().map(StoredPrediction::prediction).toList(),
                    offers,
                    prepared.profile(),
                    prepared.asOf());
        } catch (RuntimeException ex) {
            // 실패 예약만 해제한다. 이미 확정된 성공은 fail()의 시도 검증으로 보존된다.
            try {
                tx.locked(
                        user,
                        stamp,
                        owner -> {
                            prepared.work()
                                    .forEach(
                                            w ->
                                                    ledger.fail(
                                                            user,
                                                            w.ticket(),
                                                            AiFeature.TIME_PREDICTION,
                                                            "REQUEST_FAILED"));
                            return null;
                        });
            } catch (RuntimeException ignored) {
                /* 저장소 장애 시 예약은 lease 만료 뒤 회수한다. */
            }
            throw ex;
        }
    }

    private boolean valid(ClaudeGateway.Advice a) {
        return a != null
                && Double.isFinite(a.factor())
                && a.factor() >= 0.75
                && a.factor() <= 1.40
                && a.reasonCodes() != null
                && !a.reasonCodes().isEmpty()
                && a.reasonCodes().size() <= 14
                && a.reasonCodes().stream().allMatch(Objects::nonNull);
    }

    private TimePredictionResult cached(TimePredictionResult p) {
        return new TimePredictionResult(
                p.draftId(),
                p.todoId(),
                p.userEstimatedDurationMinutes(),
                p.durationCorrectionFactor(),
                p.baselinePredictionMinutes(),
                p.aiEstimatedDurationMinutes(),
                p.contextAdjustmentFactor(),
                p.mode(),
                p.claudeStatus(),
                p.voyageStatus(),
                p.reasonCodes(),
                p.similarTodos(),
                p.usage(),
                true,
                p.asOf(),
                p.profile());
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.RESOURCE_CONFLICT);
    }
}
