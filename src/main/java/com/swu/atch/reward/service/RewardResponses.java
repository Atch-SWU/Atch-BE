package com.swu.atch.reward.service;

import com.swu.atch.reward.domain.*;
import com.swu.atch.reward.dto.response.*;
import com.swu.atch.reward.asset.CharacterAssetUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

// 조회 완료된 Entity를 확정 DTO로 변환한다. 저장·소유권 조회·정산은 수행하지 않는다.
@Component
@RequiredArgsConstructor
public final class RewardResponses {
    private final CharacterAssetUrls assetUrls;

    public CharacterItemResponse item(CharacterItem item) {
        return new CharacterItemResponse(item.getId(), item.getName(), item.getGrade(), item.getCategory(),
                item.getAssetKey(), assetUrls.resolve(item.getAssetKey()), item.isActive());
    }

    public EquippedCharacterSlotResponse slot(ItemCategory category, UserEquippedItem equipped) {
        if (equipped == null) return new EquippedCharacterSlotResponse(category, null, null, null);
        if (equipped.getCategory() != category || equipped.getCharacterItem().getCategory() != category)
            throw RewardPolicy.conflict();
        return new EquippedCharacterSlotResponse(category, item(equipped.getCharacterItem()),
                equipped.getEquippedAt(), equipped.getUpdatedAt());
    }

    public CharacterResponse character(List<UserEquippedItem> equipped, long active, long ownedActive, Instant asOf) {
        var byCategory = new EnumMap<ItemCategory, UserEquippedItem>(ItemCategory.class);
        for (var row : equipped) {
            if (byCategory.put(row.getCategory(), row) != null) throw RewardPolicy.conflict();
        }
        var slots = Arrays.stream(ItemCategory.values()).map(c -> slot(c, byCategory.get(c))).toList();
        var collection = new CharacterCollectionResponse(active, ownedActive, RewardPolicy.allCollected(active, ownedActive));
        return new CharacterResponse(slots, collection, asOf);
    }

    public OwnedCharacterItemResponse owned(UserCharacterItem owned, Set<Long> equippedItemIds) {
        var item = owned.getCharacterItem();
        return new OwnedCharacterItemResponse(item(item), owned.getAcquiredAt(), equippedItemIds.contains(item.getId()));
    }

    public RewardSettlementResponse settlement(RewardSettlement settlement, List<RewardGrant> grants) {
        var eligible = RewardPolicy.eligibleGrades(settlement.getAssignedTodoCount(), settlement.getCompletedTodoCount());
        var grades = EnumSet.noneOf(ItemGrade.class);
        var items = new HashSet<Long>();
        for (var grant : grants) {
            if (!Objects.equals(grant.getUser().getId(), settlement.getUser().getId())
                    || !Objects.equals(grant.getRewardDate(), settlement.getRewardDate())
                    || grant.getCompletionRate().compareTo(settlement.getCompletionRate()) != 0
                    || grant.getRewardGrade() != grant.getCharacterItem().getGrade()
                    || !eligible.contains(grant.getRewardGrade()) || !grades.add(grant.getRewardGrade())
                    || !items.add(grant.getCharacterItem().getId())) throw RewardPolicy.conflict();
        }
        var result = grants.stream().sorted(Comparator.comparing(RewardGrant::getRewardGrade))
                .map(g -> new RewardGrantResponse(g.getId(), g.getRewardGrade(), item(g.getCharacterItem()), g.getGrantedAt()))
                .toList();
        return new RewardSettlementResponse(settlement.getId(), settlement.getRewardDate(),
                settlement.getAssignedTodoCount(), settlement.getCompletedTodoCount(), settlement.getCompletionRate(),
                eligible, result, settlement.getSettledAt());
    }
}
