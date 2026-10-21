
package com.swu.atch.setting.domain;

import com.swu.atch.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

// 사용자별 설정
@Getter
@Entity
@Table(name = "user_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSetting {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId // User의 PK를 UserSetting의 PK로 공유
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "task_switch_restriction_enabled", nullable = false)
    private boolean taskSwitchRestrictionEnabled = true;

    @Column(name = "task_switch_wait_minutes", nullable = false)
    private int taskSwitchWaitMinutes = 10;

    @Column(name = "repeat_recommendation_enabled", nullable = false)
    private boolean repeatRecommendationEnabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserSetting(User user) {
        this.user = user;
    }
}
