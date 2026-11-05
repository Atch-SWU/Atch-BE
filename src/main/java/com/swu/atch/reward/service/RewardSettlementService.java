package com.swu.atch.reward.service;

import com.swu.atch.report.domain.UserDailySettlementState;
import com.swu.atch.report.repository.TodoDailySnapshotRepository;
import com.swu.atch.reward.domain.*;
import com.swu.atch.reward.repository.*;
import com.swu.atch.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RewardSettlementService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final RewardSettlementRepository settlements;
    private final RewardGrantRepository grants;
    private final CharacterItemRepository items;
    private final UserCharacterItemRepository owned;
    private final TodoDailySnapshotRepository snapshots;

    // 호출자의 User 잠금과 트랜잭션으로 확정 구간의 누락만 과거부터 복구한다.
    public Recovery recoverLocked(User user, UserDailySettlementState state, Instant now, int budget) {
        if (state.getTrackingStartedOn().isAfter(state.getNextSettlementDate())) throw RewardPolicy.conflict();
        var dates = settlements.findMissingDates(user.getId(), state.getTrackingStartedOn(), state.getNextSettlementDate(), budget + 1);
        int count = Math.min(dates.size(), budget);
        for (int i = 0; i < count; i++) settleLocked(user, dates.get(i), now);
        return new Recovery(count, dates.size() <= budget);
    }

    public void settleLocked(User user, LocalDate date, Instant now) {
        // 후보 소진으로 지급이 없었던 날도 완료된 정산이다. 새 아이템으로 재추첨하지 않는다.
        if (settlements.existsByUser_IdAndRewardDate(user.getId(), date)) return;
        Instant boundary = date.plusDays(1).atStartOfDay(SEOUL).toInstant();
        if (now.isBefore(boundary)) throw RewardPolicy.conflict();
        var counts = snapshots.rewardCounts(user.getId(), date, boundary);
        if (counts.getInvalidSnapshotCount() != 0 || counts.getAssignedTodoCount() > Integer.MAX_VALUE
                || counts.getCompletedTodoCount() > Integer.MAX_VALUE) throw RewardPolicy.conflict();
        int assigned = (int) counts.getAssignedTodoCount(), completed = (int) counts.getCompletedTodoCount();
        var eligible = RewardPolicy.eligibleGrades(assigned, completed);
        var settlement = settlements.save(RewardSettlement.settle(user, date, assigned, completed, now));
        for (var grade : eligible) {
            var candidates = items.findActiveUnownedForGrant(user.getId(), grade);
            if (candidates.isEmpty()) continue;
            var item = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            owned.save(UserCharacterItem.acquire(user, item));
            grants.save(RewardGrant.fromSettlement(settlement, item, now));
        }
        grants.flush();
    }

    public record Recovery(int processed, boolean complete) { }
}
