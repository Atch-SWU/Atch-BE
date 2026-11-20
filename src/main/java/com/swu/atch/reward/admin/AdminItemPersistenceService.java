package com.swu.atch.reward.admin;

import com.swu.atch.reward.domain.CharacterItem;
import com.swu.atch.reward.repository.CharacterItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminItemPersistenceService {
    private final CharacterItemRepository items;
    private final AdminAccess access;

    @Transactional(readOnly = true)
    public Map<String, CharacterItem> existing(List<PreparedCharacterItem> prepared) {
        var existing = items.findAllByItemCodeIn(prepared.stream().map(item -> item.input().itemCode()).toList())
                .stream().collect(Collectors.toMap(CharacterItem::getItemCode, Function.identity()));
        for (var item : prepared) {
            var saved = existing.get(item.input().itemCode());
            if (saved != null && !matches(saved, item)) throw conflict(item.input().itemCode());
        }
        return existing;
    }

    @Transactional
    public List<CharacterItem> register(AdminPrincipal principal, List<PreparedCharacterItem> prepared, Map<String, String> keys) {
        access.requireLocked(principal);
        var existing = existing(prepared);
        var result = new ArrayList<CharacterItem>();
        for (var item : prepared) {
            var input = item.input();
            var saved = existing.get(input.itemCode());
            if (saved == null) {
                saved = items.save(CharacterItem.register(input.itemCode(), input.name(), input.grade(), input.category(),
                        Objects.requireNonNull(keys.get(input.itemCode())), item.sha256()));
            }
            result.add(saved);
        }
        items.flush();
        return result;
    }

    @Transactional(readOnly = true)
    public Set<String> referencedKeys(Collection<String> keys) {
        return items.findAllByAssetKeyIn(keys).stream().map(CharacterItem::getAssetKey).collect(Collectors.toSet());
    }

    private boolean matches(CharacterItem saved, PreparedCharacterItem item) {
        var input = item.input();
        return saved.getName().equals(input.name().trim()) && saved.getGrade() == input.grade()
                && saved.getCategory() == input.category() && Objects.equals(saved.getAssetSha256(), item.sha256());
    }

    private AdminItemException conflict(String code) {
        return new AdminItemException(HttpStatus.CONFLICT, "ITEM_CODE_CONFLICT",
                "등록 코드 '" + code + "'가 다른 내용으로 사용 중입니다. 등록 목록을 확인해주세요.");
    }
}
