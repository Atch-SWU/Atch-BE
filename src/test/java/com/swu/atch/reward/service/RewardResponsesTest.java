package com.swu.atch.reward.service;

import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.reward.domain.*;
import com.swu.atch.user.domain.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RewardResponsesTest {
    private final Instant now = Instant.parse("2026-11-05T03:00:00Z");
    private final LocalDate day = LocalDate.of(2026, 11, 4);

    @Test void returnsAllFourEmptySlotsWithoutInventingEquipment() {
        var result = RewardResponses.character(List.of(), 0, 0, now);
        assertThat(result.slots()).extracting(s -> s.category()).containsExactly(ItemCategory.values());
        assertThat(result.slots()).allSatisfy(s -> {
            assertThat(s.item()).isNull(); assertThat(s.equippedAt()).isNull(); assertThat(s.updatedAt()).isNull();
        });
        assertThat(result.collection().allActiveItemsCollected()).isFalse();
        assertThat(result.asOf()).isEqualTo(now);
    }

    @Test void preservesInactiveEquipmentAndItsTimestamp() {
        var item = item(12, ItemGrade.B, ItemCategory.EYES);
        var equipped = UserEquippedItem.equip(user(1), item, now);
        var result = RewardResponses.character(List.of(equipped), 2, 2, now);
        var slot = result.slots().get(2);
        assertThat(slot.item().id()).isEqualTo(12);
        assertThat(slot.item().active()).isFalse();
        assertThat(slot.equippedAt()).isEqualTo(now);
        assertThat(result.collection().allActiveItemsCollected()).isTrue();
    }

    @Test void rejectsWrongOrDuplicateSlots() {
        var row = UserEquippedItem.equip(user(1), item(12, ItemGrade.B, ItemCategory.EYES), now);
        assertThatThrownBy(() -> RewardResponses.slot(ItemCategory.COLOR, row)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> RewardResponses.character(List.of(row, row), 1, 1, now)).isInstanceOf(BusinessException.class);
    }

    @Test void marksOwnedItemUsingCharacterItemId() {
        var owned = UserCharacterItem.acquire(user(1), item(12, ItemGrade.B, ItemCategory.COLOR));
        assertThat(RewardResponses.owned(owned, Set.of(12L)).equipped()).isTrue();
        assertThat(RewardResponses.owned(owned, Set.of(99L)).equipped()).isFalse();
    }

    @Test void distinguishesEligibleGradesFromActualGrantAndPreservesConfirmedResult() {
        var settlement = RewardSettlement.settle(user(1), day, 4, 3, now);
        var grant = RewardGrant.fromSettlement(settlement, item(12, ItemGrade.A, ItemCategory.COLOR), now);
        var result = RewardResponses.settlement(settlement, List.of(grant));
        assertThat(result.eligibleGrades()).containsExactly(ItemGrade.B, ItemGrade.A);
        assertThat(result.grants()).hasSize(1);
        assertThat(result.grants().getFirst().rewardGrade()).isEqualTo(ItemGrade.A);
        assertThat(result.completionRate()).isEqualByComparingTo("75.00");
        assertThat(result.settledAt()).isEqualTo(now);
    }

    @Test void noGrantStillReturnsSettlementAndSortsRealGrantsInBASOrder() {
        var settlement = RewardSettlement.settle(user(1), day, 1, 1, now);
        assertThat(RewardResponses.settlement(settlement, List.of()).grants()).isEmpty();
        var s = RewardGrant.fromSettlement(settlement, item(1, ItemGrade.S, ItemCategory.COLOR), now);
        var a = RewardGrant.fromSettlement(settlement, item(2, ItemGrade.A, ItemCategory.EYES), now);
        var b = RewardGrant.fromSettlement(settlement, item(3, ItemGrade.B, ItemCategory.BACKGROUND), now);
        assertThat(RewardResponses.settlement(settlement, List.of(s, a, b)).grants())
                .extracting(g -> g.rewardGrade()).containsExactly(ItemGrade.B, ItemGrade.A, ItemGrade.S);
    }

    @Test void rejectsGrantOfAnotherOwnerOrDateAndDuplicateGrades() {
        var settlement = RewardSettlement.settle(user(1), day, 1, 1, now);
        var item = item(12, ItemGrade.B, ItemCategory.COLOR);
        for (var other : List.of(RewardSettlement.settle(user(2), day, 1, 1, now),
                RewardSettlement.settle(user(1), day.minusDays(1), 1, 1, now))) {
            var grant = RewardGrant.fromSettlement(other, item, now);
            assertThatThrownBy(() -> RewardResponses.settlement(settlement, List.of(grant))).isInstanceOf(BusinessException.class);
        }
        var b = RewardGrant.fromSettlement(settlement, item, now);
        var otherB = RewardGrant.fromSettlement(settlement, item(13, ItemGrade.B, ItemCategory.EYES), now);
        assertThatThrownBy(() -> RewardResponses.settlement(settlement, List.of(b, otherB))).isInstanceOf(BusinessException.class);
    }

    @Test void rejectsGrantAboveRawCompletionThreshold() {
        var settlement = RewardSettlement.settle(user(1), day, 2, 1, now);
        var a = RewardGrant.fromSettlement(settlement, item(12, ItemGrade.A, ItemCategory.COLOR), now);
        assertThatThrownBy(() -> RewardResponses.settlement(settlement, List.of(a))).isInstanceOf(BusinessException.class);
    }

    private User user(long id) {
        var user = mock(User.class); when(user.getId()).thenReturn(id); return user;
    }

    private CharacterItem item(long id, ItemGrade grade, ItemCategory category) {
        var item = mock(CharacterItem.class);
        when(item.getId()).thenReturn(id); when(item.getGrade()).thenReturn(grade);
        when(item.getCategory()).thenReturn(category); when(item.getName()).thenReturn("아이템");
        when(item.getAssetKey()).thenReturn("character/example");
        return item;
    }
}
