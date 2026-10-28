
package com.swu.atch.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

// 사용자 기본 정보
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8)
    private String nickname;

    @Column(length = 255)
    private String bio;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User(String nickname) {
        this.nickname = nickname;
    }

    // 기존 가입 정책과 동일한 닉네임 규칙 적용
    public void changeNickname(String nickname) {
        if (nickname == null || !nickname.matches("^[가-힣a-zA-Z]{2,8}$")) {
            throw new IllegalArgumentException("닉네임은 한글 또는 영문 2~8자여야 합니다.");
        }
        this.nickname = nickname;
    }
}
