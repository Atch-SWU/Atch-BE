package com.swu.atch.ai.service;

import com.nimbusds.jwt.SignedJWT;
import com.swu.atch.ai.domain.*;
import com.swu.atch.ai.dto.request.*;
import com.swu.atch.ai.dto.response.*;
import com.swu.atch.ai.repository.*;
import com.swu.atch.common.exception.*;
import com.swu.atch.report.service.DailySettlementService;
import com.swu.atch.todo.domain.*;
import com.swu.atch.todo.dto.request.*;
import com.swu.atch.todo.dto.response.TodoResponse;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.todo.service.*;
import com.swu.atch.user.domain.User;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiTodoService {
    private final AiTransactions tx;
    private final AiDraftService drafts;
    private final AiCreationRequestRepository creations;
    private final AiOperationRepository operations;
    private final TodoRepository todos;
    private final PredictionTokenService tokens;
    private final TodoSchedulePolicy schedule;
    private final TodoResponseMapper mapper;
    private final DailySettlementService settlements;
    private final AiJson json;

    public record Created(TodoResponse todo, boolean replayed) {}

    public record Selection(Integer aiMinutes, SelectedEstimateType type) {}

    public Created create(Long user, String stamp, TodoCreateRequest input) {
        if (input.estimateChoice() == null || input.creationRequestId() == null) throw bad();
        settlements.requirePrepared(user, stamp);
        return tx.locked(
                user,
                stamp,
                owner -> {
                    var hash =
                            json.hash(
                                    Arrays.asList(
                                            "SINGLE",
                                            input.title().trim(),
                                            input.memo(),
                                            input.startDate(),
                                            input.startTime(),
                                            input.dueDate(),
                                            input.dueTime(),
                                            input.allDay(),
                                            input.userEstimatedDurationMinutes(),
                                            choiceIntent(input.estimateChoice())));
                    var replay =
                            creations.findByUser_IdAndCreationRequestId(
                                    user, input.creationRequestId());
                    if (replay.isPresent()) {
                        if (!hash.equals(replay.get().getRequestHash())) throw conflict();
                        return new Created(
                                json.read(replay.get().getResultPayload(), TodoResponse.class),
                                true);
                    }
                    var draft = drafts.owned(input.estimateChoice().draftId(), user);
                    requireNew(draft);
                    if (draft.getParent() != null) throw conflict();
                    Instant now = settlements.requireCurrentLocked(owner);
                    schedule.requireNewStartDate(input.startDate(), now);
                    var times =
                            schedule.schedule(
                                    input.startDate(),
                                    input.startTime(),
                                    input.dueDate(),
                                    input.dueTime(),
                                    input.allDay());
                    var offers = offers(user, stamp, draft.getDraftId(), input.estimateChoice());
                    requireOfferCount(offers, 1, input.estimateChoice());
                    var selection =
                            selection(
                                    input.estimateChoice(),
                                    offers.get(draft.getDraftId()),
                                    input.title(),
                                    null,
                                    input.memo(),
                                    input.userEstimatedDurationMinutes(),
                                    null);
                    var todo =
                            make(
                                    owner,
                                    input.title(),
                                    null,
                                    input.memo(),
                                    times.startAt(),
                                    times.dueAt(),
                                    input.startDate(),
                                    input.allDay(),
                                    input.userEstimatedDurationMinutes(),
                                    selection);
                    todos.saveAndFlush(todo);
                    draft.attach(todo, now);
                    var response = mapper.toResponse(todo, 0, now);
                    creations.saveAndFlush(
                            new AiCreationRequest(
                                    owner,
                                    draft,
                                    input.creationRequestId(),
                                    hash,
                                    json.write(response)));
                    return new Created(response, false);
                });
    }

    public DecomposedTodoCreateResponse confirm(
            Long user, String stamp, DecomposedTodoCreateRequest input) {
        settlements.requirePrepared(user, stamp);
        return tx.locked(
                user,
                stamp,
                owner -> {
                    String hash =
                            json.hash(
                                    Arrays.asList(
                                            "DECOMPOSED",
                                            input.decompositionDraftId(),
                                            input.sourceTaskName().trim(),
                                            input.memo(),
                                            input.startDate(),
                                            input.startTime(),
                                            input.dueDate(),
                                            input.dueTime(),
                                            input.allDay(),
                                            input.tasks().stream()
                                                    .map(
                                                            t ->
                                                                    List.of(
                                                                            t.draftId(),
                                                                            t.title().trim(),
                                                                            t
                                                                                    .userEstimatedDurationMinutes()))
                                                    .toList(),
                                            choiceIntent(input.estimateChoice())));
                    var replay =
                            creations.findByUser_IdAndCreationRequestId(
                                    user, input.creationRequestId());
                    if (replay.isPresent()) {
                        if (!hash.equals(replay.get().getRequestHash())) throw conflict();
                        var old =
                                json.read(
                                        replay.get().getResultPayload(),
                                        DecomposedTodoCreateResponse.class);
                        return new DecomposedTodoCreateResponse(
                                old.creationRequestId(),
                                old.items(),
                                old.totalUserEstimatedDurationMinutes(),
                                old.totalAiEstimatedDurationMinutes(),
                                old.selectedEstimateType(),
                                true);
                    }
                    var root = drafts.owned(input.decompositionDraftId(), user);
                    requireNew(root);
                    if (root.getParent() != null) throw conflict();
                    var operation =
                            operations.findByDraft_IdAndFeature(
                                    root.getId(), AiFeature.DECOMPOSITION);
                    if (operation.isEmpty()
                            || operation.get().getState() != AiOperationState.SUCCEEDED)
                        throw conflict();
                    if (input.estimateChoice() != null
                            && !input.decompositionDraftId()
                                    .equals(input.estimateChoice().draftId())) throw bad();
                    Instant now = settlements.requireCurrentLocked(owner);
                    schedule.requireNewStartDate(input.startDate(), now);
                    var times =
                            schedule.schedule(
                                    input.startDate(),
                                    input.startTime(),
                                    input.dueDate(),
                                    input.dueTime(),
                                    input.allDay());
                    var offers = offers(user, stamp, root.getDraftId(), input.estimateChoice());
                    requireOfferCount(offers, input.tasks().size(), input.estimateChoice());
                    var seen = new HashSet<UUID>();
                    var children = new ArrayList<AiDraft>();
                    var selections = new ArrayList<Selection>();
                    for (var task : input.tasks()) {
                        if (!seen.add(task.draftId())) throw bad();
                        var child = drafts.owned(task.draftId(), user);
                        requireNew(child);
                        if (child.getParent() == null
                                || !child.getParent().getId().equals(root.getId()))
                            throw conflict();
                        children.add(child);
                        selections.add(
                                selection(
                                        input.estimateChoice(),
                                        offers.get(child.getDraftId()),
                                        task.title(),
                                        input.sourceTaskName().trim(),
                                        input.memo(),
                                        task.userEstimatedDurationMinutes(),
                                        null));
                    }
                    var items = new ArrayList<DecomposedTodoCreateResponse.CreatedTodo>();
                    long userTotal = 0, aiTotal = 0;
                    boolean allAi = true;
                    for (int i = 0; i < input.tasks().size(); i++) {
                        var task = input.tasks().get(i);
                        var choice = selections.get(i);
                        var todo =
                                make(
                                        owner,
                                        task.title(),
                                        input.sourceTaskName().trim(),
                                        input.memo(),
                                        times.startAt(),
                                        times.dueAt(),
                                        input.startDate(),
                                        input.allDay(),
                                        task.userEstimatedDurationMinutes(),
                                        choice);
                        todos.saveAndFlush(todo);
                        children.get(i).attach(todo, now);
                        items.add(
                                new DecomposedTodoCreateResponse.CreatedTodo(
                                        task.draftId(), mapper.toResponse(todo, 0, now)));
                        userTotal += task.userEstimatedDurationMinutes();
                        if (choice.aiMinutes() == null) allAi = false;
                        else aiTotal += choice.aiMinutes();
                    }
                    root.finalizeDraft(now);
                    var type =
                            input.estimateChoice() == null
                                    ? SelectedEstimateType.USER
                                    : input.estimateChoice().selectedEstimateType();
                    var response =
                            new DecomposedTodoCreateResponse(
                                    input.creationRequestId(),
                                    List.copyOf(items),
                                    userTotal,
                                    allAi ? aiTotal : null,
                                    type,
                                    false);
                    creations.saveAndFlush(
                            new AiCreationRequest(
                                    owner,
                                    root,
                                    input.creationRequestId(),
                                    hash,
                                    json.write(response)));
                    return response;
                });
    }

    // TodoService가 User→Todo 잠금과 상태 검증을 마친 뒤 호출한다.
    public Selection resolveExisting(Long user, String stamp, Todo todo, TodoUpdateRequest input) {
        if (input.estimateChoice() == null) return null;
        var draft = drafts.owned(input.estimateChoice().draftId(), user);
        if (draft.getTodo() == null || !draft.getTodo().getId().equals(todo.getId()))
            throw conflict();
        var offers = offers(user, stamp, draft.getDraftId(), input.estimateChoice());
        requireOfferCount(offers, 1, input.estimateChoice());
        return selection(
                input.estimateChoice(),
                offers.get(draft.getDraftId()),
                input.title(),
                todo.getSourceTaskName(),
                input.memo(),
                input.userEstimatedDurationMinutes(),
                todo);
    }

    private Selection selection(
            EstimateChoiceRequest choice,
            PredictionTokenService.Offer offer,
            String title,
            String source,
            String memo,
            int minutes,
            Todo todo) {
        var type = choice == null ? SelectedEstimateType.USER : choice.selectedEstimateType();
        if (offer == null) {
            if (type == SelectedEstimateType.AI
                    || choice != null && choice.predictionToken() != null) throw invalid();
            return new Selection(null, type);
        }
        String hash = PredictionInputs.hash(json, title, source, memo, minutes);
        if (!hash.equals(offer.inputHash())) throw invalid();
        if (todo == null && !offer.todoVersion().isEmpty()) throw invalid();
        if (todo != null && !todo.getUpdatedAt().toString().equals(offer.todoVersion())) {
            // 첫 저장이 성공한 동일 PUT의 재전송만 허용한다. 다른 편집을 덮어쓰지 않는다.
            boolean same =
                    PredictionInputs.hash(
                                            json,
                                            todo.getTitle(),
                                            todo.getSourceTaskName(),
                                            todo.getMemo(),
                                            todo.getUserEstimatedDurationMinutes())
                                    .equals(hash)
                            && Objects.equals(todo.getAiEstimatedDurationMinutes(), offer.minutes())
                            && todo.getSelectedEstimateType() == type;
            if (!same) throw invalid();
        }
        return new Selection(offer.minutes(), type);
    }

    private Map<UUID, PredictionTokenService.Offer> offers(
            Long user, String stamp, UUID root, EstimateChoiceRequest choice) {
        if (choice == null || choice.predictionToken() == null) {
            if (choice != null && choice.selectedEstimateType() == SelectedEstimateType.AI)
                throw invalid();
            return Map.of();
        }
        return tokens.verify(choice.predictionToken(), user, stamp, root);
    }

    private void requireOfferCount(
            Map<UUID, PredictionTokenService.Offer> offers,
            int size,
            EstimateChoiceRequest choice) {
        if (choice != null && choice.predictionToken() != null && offers.size() != size)
            throw invalid();
    }

    private Object choiceIntent(EstimateChoiceRequest choice) {
        if (choice == null) return List.of("USER");
        Object intent = "";
        if (choice.predictionToken() != null) {
            // 재전송 비교에만 만료와 독립인 의미 값을 사용한다. 신규 저장 권한은 verify()가 검증한다.
            try {
                intent =
                        SignedJWT.parse(choice.predictionToken())
                                .getJWTClaimsSet()
                                .getClaim("offers");
            } catch (java.text.ParseException ex) {
                throw invalid();
            }
        }
        return Arrays.asList(choice.draftId(), choice.selectedEstimateType(), intent);
    }

    private void requireNew(AiDraft draft) {
        if (draft.getTodo() != null
                || draft.getFinalizedAt() != null
                || !draft.getExpiresAt().isAfter(Instant.now())
                || creations.existsByDraft_Id(draft.getId())) throw conflict();
    }

    private Todo make(
            User user,
            String title,
            String source,
            String memo,
            Instant start,
            Instant due,
            java.time.LocalDate date,
            boolean allDay,
            int minutes,
            Selection selection) {
        return Todo.builder()
                .user(user)
                .title(title)
                .sourceTaskName(source)
                .memo(memo)
                .startAt(start)
                .dueAt(due)
                .currentScheduleDate(date)
                .allDay(allDay)
                .userEstimatedDurationMinutes(minutes)
                .userEstimateSource(UserEstimateSource.USER_INPUT)
                .aiEstimatedDurationMinutes(selection.aiMinutes())
                .selectedEstimateType(selection.type())
                .build();
    }

    private BusinessException bad() {
        return new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    private BusinessException conflict() {
        return new BusinessException(ErrorCode.RESOURCE_CONFLICT);
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.AI_OFFER_INVALID);
    }
}
