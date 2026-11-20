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
    private final RewardResponses responses;

    public CharacterResponse current(Long id, String stamp) {
        return access.execute(id, stamp, ctx -> {
            var counts = items.collectionCounts(id);
            return responses.character(equipped.findAllByUser_IdOrderByCategoryAsc(id),
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
            var result = page.getContent().stream().map(row -> responses.owned(row, equippedIds)).toList();
            return new OwnedCharacterItemPageResponse(result, page.getNumber(), page.getSize(), page.getTotalElements(),
                    page.getTotalPages(), page.hasNext(), ctx.now());
        });
    }

    public CharacterItemCatalogPageResponse catalog(Long id, String stamp, CharacterItemCatalogQuery query) {
        RewardPolicy.page(query.page(), query.size());
        return access.execute(id, stamp, ctx -> {
            var page = items.findCatalog(id, query.category(), query.grade(),
                    PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.ASC, "id")));
            var equippedIds = equipped.findAllByUser_IdOrderByCategoryAsc(id).stream()
                    .map(row -> row.getCharacterItem().getId()).collect(Collectors.toSet());
            var result = page.getContent().stream().map(row -> new CharacterItemCatalogEntryResponse(
                    responses.item(row.getItem()), row.getOwned(),
                    row.getOwned() && equippedIds.contains(row.getItem().getId()), row.getAcquiredAt())).toList();
            return new CharacterItemCatalogPageResponse(result, page.getNumber(), page.getSize(), page.getTotalElements(),
                    page.getTotalPages(), page.hasNext(), ctx.now());
        });
    }

    public CharacterItemResponse item(Long id, String stamp, Long itemId) {
        if (itemId == null || itemId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return access.execute(id, stamp, ctx -> {
            var item = items.findById(itemId).orElseThrow(CharacterService::missing);
            // 활성 아이템은 미획득 상태에서도 열람하며, 장착에는 기존 보유 검사를 적용한다.
            if (!item.isActive() && !owned.existsByUser_IdAndCharacterItem_Id(id, itemId)) throw missing();
            return responses.item(item);
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
            return responses.slot(category, row);
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
