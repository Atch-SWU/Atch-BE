package com.swu.atch.report.domain;

import com.swu.atch.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.time.LocalDate;

// 빈 날짜도 포함하여 시작 날짜 이상·다음 대상 날짜 미만이 확정됐음을 나타낸다.
@Entity
@Table(name = "user_daily_settlement_states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDailySettlementState {
    @Id @Column(name = "user_id") private Long userId;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "tracking_started_on", nullable = false) private LocalDate trackingStartedOn;
    @Column(name = "next_settlement_date", nullable = false) private LocalDate nextSettlementDate;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    public UserDailySettlementState(User user, LocalDate today) {
        this.user = java.util.Objects.requireNonNull(user);
        trackingStartedOn = java.util.Objects.requireNonNull(today); nextSettlementDate = today;
    }
    public void settle(LocalDate date) {
        if (!nextSettlementDate.equals(date)) throw new IllegalStateException("확정 대상 날짜가 아닙니다.");
        nextSettlementDate = date.plusDays(1);
    }
}
