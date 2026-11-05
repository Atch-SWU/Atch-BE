package com.swu.atch.reward.service;

import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.reward.domain.ItemGrade;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// DB나 표시용 반올림에 의존하지 않는 보상 판정 규칙이다.
public final class RewardPolicy {
    private RewardPolicy() { }

    public static List<ItemGrade> eligibleGrades(int assigned, int completed) {
        if (assigned < 0 || completed < 0 || completed > assigned) throw conflict();
        if (assigned == 0 || 2L * completed < assigned) return List.of();
        if (completed == assigned) return List.of(ItemGrade.B, ItemGrade.A, ItemGrade.S);
        if (4L * completed >= 3L * assigned) return List.of(ItemGrade.B, ItemGrade.A);
        return List.of(ItemGrade.B);
    }

    public static boolean allCollected(long active, long ownedActive) {
        if (active < 0 || ownedActive < 0 || ownedActive > active) throw conflict();
        return active > 0 && ownedActive == active;
    }

    public static void range(LocalDate from, LocalDate to, LocalDate today) {
        if (from == null || to == null || today == null || from.isAfter(to)
                || !to.isBefore(today) || ChronoUnit.DAYS.between(from, to) > 365) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    public static void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    static BusinessException conflict() {
        return new BusinessException(ErrorCode.RESOURCE_CONFLICT);
    }
}
