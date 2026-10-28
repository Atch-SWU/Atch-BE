package com.swu.atch.reward.domain;

import com.swu.atch.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;

// 사용자가 획득한 캐릭터 아이템
@Getter
@Entity
@Table(
        name = "user_character_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_character_items_user_item",
                columnNames = {"user_id", "character_item_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCharacterItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "character_item_id",
            nullable = false,
            updatable = false
    )
    private CharacterItem characterItem;

    @CreationTimestamp
    @Column(name = "acquired_at", nullable = false, updatable = false)
    private Instant acquiredAt;

    // 신규 아이템 획득
    public static UserCharacterItem acquire(
            User user,
            CharacterItem characterItem
    ) {
        UserCharacterItem ownedItem = new UserCharacterItem();

        ownedItem.user = Objects.requireNonNull(
                user, "사용자는 필수입니다."
        );
        ownedItem.characterItem = Objects.requireNonNull(
                characterItem, "아이템은 필수입니다."
        );

        return ownedItem;
    }
}
