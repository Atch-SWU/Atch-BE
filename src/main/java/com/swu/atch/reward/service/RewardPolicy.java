package com.swu.atch.reward.service;

import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.reward.domain.ItemGrade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// DB나 표시용 반올림에 의존하지 않는 보상 판정 규칙이다.
public final class RewardPolicy {
    private static final List<GradeThreshold> THRESHOLDS = List.of(
            new GradeThreshold(ItemGrade.B, 50), new GradeThreshold(ItemGrade.A, 75), new GradeThreshold(ItemGrade.S, 100));

    private RewardPolicy() { }

    public static List<ItemGrade> eligibleGrades(int assigned, int completed) {
        validateCounts(assigned, completed);
        if (assigned == 0) return List.of();
        return THRESHOLDS.stream()
                .filter(threshold -> completed >= threshold.requiredCompleted(assigned))
                .map(GradeThreshold::grade).toList();
    }

    static Optional<GradeThreshold> nextThreshold(int assigned, int completed, Collection<ItemGrade> availableGrades) {
        validateCounts(assigned, completed);
        if (assigned == 0) return Optional.empty();
        return THRESHOLDS.stream()
                .filter(threshold -> completed < threshold.requiredCompleted(assigned) && availableGrades.contains(threshold.grade()))
                .findFirst();
    }

    private static void validateCounts(int assigned, int completed) {
        if (assigned < 0 || completed < 0 || completed > assigned) throw conflict();
    }

    record GradeThreshold(ItemGrade grade, int percent) {
        int requiredCompleted(int assigned) {
            // 정수 작업 수의 올림으로 표시용 퍼센트와 독립적으로 기준을 판정한다.
            return (int) ((percent * (long) assigned + 99) / 100);
        }

        BigDecimal remainingPercentagePoints(int assigned, int completed) {
            long difference = percent * (long) assigned - 100L * completed;
            return BigDecimal.valueOf(difference).divide(BigDecimal.valueOf(assigned), 2, RoundingMode.CEILING);
        }
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
