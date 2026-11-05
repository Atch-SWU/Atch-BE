package com.swu.atch.reward.service;

import com.swu.atch.common.exception.*;
import com.swu.atch.report.repository.UserDailySettlementStateRepository;
import com.swu.atch.reward.domain.RewardGrant;
import com.swu.atch.reward.dto.request.RewardSettlementListQuery;
import com.swu.atch.reward.dto.response.*;
import com.swu.atch.reward.repository.*;
import com.swu.atch.todo.service.TodoSchedulePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

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
                    .map(row -> RewardResponses.settlement(row, byDate.getOrDefault(row.getRewardDate(), List.of()))).toList();
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
            return RewardResponses.settlement(row, grants.findAllByUser_IdAndRewardDateInOrderByRewardDateDescIdAsc(id, List.of(date)));
        });
    }
}
