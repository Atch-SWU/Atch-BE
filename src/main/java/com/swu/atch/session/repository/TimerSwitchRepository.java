package com.swu.atch.session.repository;

import com.swu.atch.session.domain.TimerSwitch;
import com.swu.atch.session.domain.TimerSwitchState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TimerSwitchRepository extends JpaRepository<TimerSwitch, Long> {
    Optional<TimerSwitch> findByRequestIdAndUser_Id(UUID requestId, Long userId);
    Optional<TimerSwitch> findByUser_IdAndState(Long userId, TimerSwitchState state);
    Optional<TimerSwitch> findFirstByUser_IdAndSourceSession_IdAndTargetTodo_IdAndStateOrderByIdDesc(
            Long userId, Long sourceSessionId, Long targetTodoId, TimerSwitchState state);
}
