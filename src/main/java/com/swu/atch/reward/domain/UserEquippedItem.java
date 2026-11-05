package com.swu.atch.reward.domain;

import com.swu.atch.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

// 사용자의 현재 장착 아이템
@Getter
@Entity
@Table(
        name = "user_equipped_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_equipped_items_user_category",
                columnNames = {"user_id", "category"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEquippedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_item_id", nullable = false)
    private CharacterItem characterItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ItemCategory category;

    // 현재 아이템을 장착한 시각
    @Column(name = "equipped_at", nullable = false)
    private Instant equippedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 카테고리별 최초 장착
    // 같은 슬롯의 아이템 교체는 replaceItem을 사용한다.
    public static UserEquippedItem equip(
            User user,
            CharacterItem characterItem,
            Instant equippedAt
    ) {
        Objects.requireNonNull(user, "사용자는 필수입니다.");
        Objects.requireNonNull(
                characterItem, "장착할 아이템은 필수입니다."
        );
        Objects.requireNonNull(
                equippedAt, "장착 시각은 필수입니다."
        );

        UserEquippedItem equippedItem = new UserEquippedItem();

        equippedItem.user = user;
        equippedItem.characterItem = characterItem;
        equippedItem.category = Objects.requireNonNull(
                characterItem.getCategory(),
                "아이템 카테고리는 필수입니다."
        );
        equippedItem.equippedAt = equippedAt;

        return equippedItem;
    }

    // 소유권은 Service에서 검증하고, 이 메서드는 슬롯과 장착 시각을 유지한다.
    public void replaceItem(CharacterItem item, Instant now) {
        Objects.requireNonNull(item, "장착 아이템은 필수입니다.");
        Objects.requireNonNull(now, "장착 시각은 필수입니다.");
        if (item.getId() == null || item.getCategory() != category) {
            throw new IllegalArgumentException("저장된 동일 카테고리 아이템만 교체할 수 있습니다.");
        }
        if (Objects.equals(characterItem.getId(), item.getId())) return;
        characterItem = item;
        equippedAt = now;
    }

}
