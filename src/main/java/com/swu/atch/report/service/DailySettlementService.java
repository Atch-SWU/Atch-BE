package com.swu.atch.report.service;

import com.swu.atch.auth.service.AccountCredentials;
import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.report.domain.*;
import com.swu.atch.report.repository.*;
import com.swu.atch.todo.domain.*;
import com.swu.atch.todo.repository.TodoRepository;
import com.swu.atch.todo.service.TodoSchedulePolicy;
import com.swu.atch.user.domain.User;
import com.swu.atch.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.Objects;

@Service
public class DailySettlementService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int BATCH_DAYS = 31;
    private final UserRepository users;
    private final TodoRepository todos;
    private final TodoDailySnapshotRepository snapshots;
    private final UserDailySettlementStateRepository states;
    private final AccountCredentials credentials;
    private final TodoSchedulePolicy clock;
    private final TransactionTemplate batch;

    public DailySettlementService(UserRepository users, TodoRepository todos, TodoDailySnapshotRepository snapshots,
            UserDailySettlementStateRepository states, AccountCredentials credentials,
            TodoSchedulePolicy clock, PlatformTransactionManager transactions) {
        this.users = users; this.todos = todos; this.snapshots = snapshots; this.states = states;
        this.credentials = credentials; this.clock = clock;
        batch = new TransactionTemplate(transactions);
        batch.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // 독립 호출(스케줄러)은 날짜 묶음별 커밋, 기존 업무 트랜잭션에서는 함께 확정한다.
    // 기존 User 잠금을 가진 호출을 별도 트랜잭션으로 중단하면 교착될 수 있어 참여한다.
    public boolean prepare(Long userId, String stamp) {
        var transaction = new TransactionTemplate(batch.getTransactionManager());
        transaction.setPropagationBehavior(TransactionSynchronizationManager.isActualTransactionActive()
                ? TransactionDefinition.PROPAGATION_REQUIRED : TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return Boolean.TRUE.equals(transaction.execute(status -> {
            var user = users.findByIdForUpdate(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
            if (stamp != null) credentials.requireCurrent(credentials.localAccount(userId), stamp);
            return settleLocked(user, clock.now());
        }));
    }

    public void requirePrepared(Long userId, String stamp) {
        if (!prepare(userId, stamp)) throw new BusinessException(ErrorCode.DAILY_SETTLEMENT_PENDING);
    }

    // User 잠금 이후 한 번 더 확인하여 preflight와 실제 명령 사이 자정도 처리한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public Instant requireCurrentLocked(User user) {
        Instant now = clock.now();
        if (!settleLocked(user, now)) throw new BusinessException(ErrorCode.DAILY_SETTLEMENT_PENDING);
        return now;
    }

    private boolean settleLocked(User user, Instant now) {
        LocalDate today = clock.seoulDate(now);
        var state = states.findById(user.getId()).orElseGet(() -> {
            // 도입 이전의 상태를 과거 Snapshot으로 추측하지 않는다.
            for (var todo : todos.findByUser_IdAndDeletedAtIsNullAndCurrentScheduleDateBeforeAndStatusNot(
                    user.getId(), today, TodoStatus.COMPLETED)) todo.rollOverTo(today);
            return states.save(new UserDailySettlementState(user, today));
        });
        int days = 0;
        while (state.getNextSettlementDate().isBefore(today) && days++ < BATCH_DAYS) {
            LocalDate date = state.getNextSettlementDate();
            Instant boundary = date.plusDays(1).atStartOfDay(SEOUL).toInstant();
            for (var todo : todos.findAllByUser_IdAndCurrentScheduleDateAndDeletedAtIsNullOrderByStartAtAscIdAsc(user.getId(), date)) {
                boolean overdue = todo.getStatus() == TodoStatus.COMPLETED
                        ? todo.getCompletedAt() != null && todo.getCompletedAt().isAfter(todo.getDueAt())
                        : !boundary.isBefore(todo.getDueAt());
                var expected = TodoDailySnapshot.capture(todo, date,
                        date.isAfter(clock.seoulDate(todo.getStartAt())), overdue, boundary);
                var existing = snapshots.findByTodo_IdAndUser_IdAndScheduleDate(todo.getId(), user.getId(), date);
                if (existing.isPresent()) {
                    // 수동/구버전 기록과 모순이면 덮어쓰지 않고 복구를 중단한다.
                    if (!same(existing.get(), expected)) throw new BusinessException(ErrorCode.RESOURCE_CONFLICT);
                } else snapshots.save(expected);
                if (todo.getStatus() != TodoStatus.COMPLETED) todo.rollOverTo(date.plusDays(1));
            }
            state.settle(date);
        }
        states.flush();
        return !state.getNextSettlementDate().isBefore(today);
    }

    private boolean same(TodoDailySnapshot a, TodoDailySnapshot b) {
        return a.getStatusAtDayEnd() == b.getStatusAtDayEnd()
                && a.getStartAtSnapshot().equals(b.getStartAtSnapshot()) && a.getDueAtSnapshot().equals(b.getDueAtSnapshot())
                && Objects.equals(a.getCompletedAtSnapshot(), b.getCompletedAtSnapshot())
                && a.isWasRolledOver() == b.isWasRolledOver() && a.isWasOverdue() == b.isWasOverdue()
                && a.getSnapshotAt().equals(b.getSnapshotAt());
    }
}
