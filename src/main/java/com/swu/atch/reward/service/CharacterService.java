package com.swu.atch.reward.service;

import com.swu.atch.common.exception.*;
import com.swu.atch.reward.domain.*;
import com.swu.atch.reward.dto.request.*;
import com.swu.atch.reward.dto.response.*;
import com.swu.atch.reward.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CharacterService {
    private final RewardAccess access;
    private final CharacterItemRepository items;
    private final UserCharacterItemRepository owned;
    private final UserEquippedItemRepository equipped;

    public CharacterResponse current(Long id, String stamp) {
        return access.execute(id, stamp, ctx -> {
            var counts = items.collectionCounts(id);
            return RewardResponses.character(equipped.findAllByUser_IdOrderByCategoryAsc(id),
                    counts.getActiveItemCount(), counts.getOwnedActiveItemCount(), ctx.now());
        });
    }

    public OwnedCharacterItemPageResponse items(Long id, String stamp, CharacterItemListQuery query) {
        RewardPolicy.page(query.page(), query.size());
        return access.execute(id, stamp, ctx -> {
            var page = owned.findOwned(id, query.grade(), query.category(), PageRequest.of(query.page(), query.size(),
                    Sort.by(Sort.Direction.DESC, "acquiredAt", "id")));
            var equippedIds = equipped.findAllByUser_IdOrderByCategoryAsc(id).stream()
                    .map(row -> row.getCharacterItem().getId()).collect(Collectors.toSet());
            var result = page.getContent().stream().map(row -> RewardResponses.owned(row, equippedIds)).toList();
            return new OwnedCharacterItemPageResponse(result, page.getNumber(), page.getSize(), page.getTotalElements(),
                    page.getTotalPages(), page.hasNext(), ctx.now());
        });
    }

    public EquippedCharacterSlotResponse equip(Long id, String stamp, ItemCategory category, EquipCharacterItemRequest request) {
        if (category == null || request == null || request.characterItemId() == null || request.characterItemId() <= 0)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return access.execute(id, stamp, ctx -> {
            if (!owned.existsByUser_IdAndCharacterItem_Id(id, request.characterItemId())) throw missing();
            var item = items.findById(request.characterItemId()).orElseThrow(CharacterService::missing);
            if (item.getCategory() != category) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            var row = equipped.findByUser_IdAndCategory(id, category).orElse(null);
            if (row == null) row = equipped.save(UserEquippedItem.equip(ctx.user(), item, ctx.now()));
            else row.replaceItem(item, ctx.now());
            equipped.flush();
            return RewardResponses.slot(category, row);
        });
    }

    public void unequip(Long id, String stamp, ItemCategory category) {
        if (category == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        access.execute(id, stamp, ctx -> {
            equipped.findByUser_IdAndCategory(id, category).ifPresent(equipped::delete);
            equipped.flush();
            return null;
        });
    }

    private static BusinessException missing() { return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); }
}
