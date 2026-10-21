
package com.swu.atch.auth.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

// 사용자 인증 수단
@Getter
@Entity
@Table(name = "auth_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING) // enum 이름을 DB 문자열로 저장
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(length = 320)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "provider_user_id", length = 255)
    private String providerUserId;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // LOCAL 계정 생성
    public static AuthAccount local(
            User user,
            String email,
            String passwordHash
    ) {
        AuthAccount account = new AuthAccount();
        account.user = user;
        account.provider = AuthProvider.LOCAL;
        account.email = email;
        account.passwordHash = passwordHash;
        return account;
    }

    // 소셜 계정 생성
    public static AuthAccount social(
            User user,
            AuthProvider provider,
            String email,
            String providerUserId
    ) {
        if (provider == null || provider == AuthProvider.LOCAL) {
            throw new IllegalArgumentException("소셜 인증 제공자가 아닙니다.");
        }

        AuthAccount account = new AuthAccount();
        account.user = user;
        account.provider = provider;
        account.email = email;
        account.providerUserId = providerUserId;
        return account;
    }

    // 로그인 성공 시각 갱신
    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }
}
