package com.swu.atch.reward.service;

import com.swu.atch.common.exception.*;
import com.swu.atch.recurrence.service.RecurrenceGenerationService;
import com.swu.atch.report.repository.UserDailySettlementStateRepository;
import com.swu.atch.reward.domain.RewardGrant;
import com.swu.atch.reward.dto.request.RewardSettlementListQuery;
import com.swu.atch.reward.dto.response.*;
import com.swu.atch.reward.repository.*;
import com.swu.atch.todo.domain.TodoStatus;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.todo.service.TodoSchedulePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RewardService {
    private final RewardAccess access;
    private final RewardSettlementRepository settlements;
    private final RewardGrantRepository grants;
    private final UserDailySettlementStateRepository states;
    private final TodoSchedulePolicy clock;
    private final RewardResponses responses;
    private final RecurrenceGenerationService recurrence;
    private final TodoRepository todos;
    private final CharacterItemRepository items;

    public RewardProgressResponse progress(Long id, String stamp) {
        return access.execute(id, stamp, ctx -> {
            var date = clock.seoulDate(ctx.now());
            recurrence.generateTodayLocked(ctx.user(), ctx.now());
            var planned = todos.findAllByUser_IdAndCurrentScheduleDateAndDeletedAtIsNullOrderByStartAtAscIdAsc(id, date);
            int assigned = planned.size();
            int completed = (int) planned.stream().filter(todo -> todo.getStatus() == TodoStatus.COMPLETED).count();
            var percent = assigned == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(100L * completed).divide(BigDecimal.valueOf(assigned), 2, RoundingMode.DOWN);
            var target = assigned == completed ? null
                    : RewardPolicy.nextThreshold(assigned, completed, items.findGradesWithActiveUnownedItems(id))
                            .map(threshold -> new RewardProgressResponse.NextReward(threshold.grade(), threshold.percent(),
                                    threshold.remainingPercentagePoints(assigned, completed), threshold.requiredCompleted(assigned) - completed))
                            .orElse(null);
            return new RewardProgressResponse(date, ctx.now(), assigned, completed, percent, target);
        });
    }

    public RewardSettlementPageResponse list(Long id, String stamp, RewardSettlementListQuery query) {
        RewardPolicy.range(query.from(), query.to(), clock.seoulDate(clock.now()));
        RewardPolicy.page(query.page(), query.size());
        return access.execute(id, stamp, ctx -> {
            RewardPolicy.range(query.from(), query.to(), clock.seoulDate(ctx.now()));
            var page = settlements.findAllByUser_IdAndRewardDateBetween(id, query.from(), query.to(),
                    PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.DESC, "rewardDate")));
            var dates = page.getContent().stream().map(row -> row.getRewardDate()).toList();
            var byDate = (dates.isEmpty() ? List.<RewardGrant>of()
                    : grants.findAllByUser_IdAndRewardDateInOrderByRewardDateDescIdAsc(id, dates))
                    .stream().collect(Collectors.groupingBy(RewardGrant::getRewardDate));
            var result = page.getContent().stream()
                    .map(row -> responses.settlement(row, byDate.getOrDefault(row.getRewardDate(), List.of()))).toList();
            return new RewardSettlementPageResponse(query.from(), query.to(), result, page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages(), page.hasNext(), ctx.now());
        });
    }

    public RewardSettlementResponse detail(Long id, String stamp, LocalDate date) {
        RewardPolicy.range(date, date, clock.seoulDate(clock.now()));
        return access.execute(id, stamp, ctx -> {
            RewardPolicy.range(date, date, clock.seoulDate(ctx.now()));
            var row = settlements.findByUser_IdAndRewardDate(id, date).orElseGet(() -> {
                var state = states.findById(id).orElse(null);
                throw new BusinessException(state == null || date.isBefore(state.getTrackingStartedOn())
                        ? ErrorCode.RESOURCE_NOT_FOUND : ErrorCode.REWARD_SETTLEMENT_PENDING);
            });
            return responses.settlement(row, grants.findAllByUser_IdAndRewardDateInOrderByRewardDateDescIdAsc(id, List.of(date)));
        });
    }
}
