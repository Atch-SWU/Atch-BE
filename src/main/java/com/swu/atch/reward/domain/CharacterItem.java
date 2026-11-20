package com.swu.atch.reward.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

// 캐릭터 아이템 카탈로그
@Getter
@Entity
@Table(name = "character_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private ItemGrade grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ItemCategory category;

    @Column(name = "asset_key", nullable = false, length = 500)
    private String assetKey;

    // 이전 카탈로그는 null을 유지하고 운영진 등록부터 중복 요청을 식별한다.
    @Column(name = "item_code", length = 80, unique = true, updatable = false)
    private String itemCode;

    @Column(name = "asset_sha256", length = 64, updatable = false)
    private String assetSha256;

    // 신규 보상 지급 가능 여부
    @Column(nullable = false)
    private boolean active; // active=false로 변경하는 메서드는 향후 카탈로그 관리 기능을 구현할 때 추가

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private CharacterItem(
            String name,
            ItemGrade grade,
            ItemCategory category,
            String assetKey
    ) {
        if (name == null || name.isBlank()
                || name.length() > 100) {
            throw new IllegalArgumentException(
                    "아이템 이름은 1~100자의 공백이 아닌 문자열이어야 합니다."
            );
        }

        if (assetKey == null || assetKey.isBlank()
                || assetKey.length() > 500) {
            throw new IllegalArgumentException(
                    "아이템 자산 키는 1~500자의 공백이 아닌 문자열이어야 합니다."
            );
        }

        this.grade = Objects.requireNonNull(
                grade, "아이템 등급은 필수입니다."
        );
        this.category = Objects.requireNonNull(
                category, "아이템 카테고리는 필수입니다."
        );
        this.name = name.trim();
        this.assetKey = assetKey.trim();
        this.active = true;
    }

    public static CharacterItem register(String itemCode, String name, ItemGrade grade,
            ItemCategory category, String assetKey, String assetSha256) {
        if (itemCode == null || !itemCode.matches("[a-z0-9][a-z0-9-]{0,79}")
                || assetSha256 == null || !assetSha256.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("아이템 등록 코드와 이미지 해시를 확인해주세요.");
        }
        var item = new CharacterItem(name, grade, category, assetKey);
        item.itemCode = itemCode;
        item.assetSha256 = assetSha256;
        return item;
    }
}
