package com.swu.atch.reward.service;

import com.swu.atch.common.exception.BusinessException;
import com.swu.atch.common.exception.ErrorCode;
import com.swu.atch.reward.domain.ItemGrade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class RewardPolicyTest {
    static Stream<Arguments> thresholds() {
        return Stream.of(
                Arguments.of(0, 0, List.of()),
                Arguments.of(4, 1, List.of()),
                Arguments.of(4, 2, List.of(ItemGrade.B)),
                Arguments.of(4, 3, List.of(ItemGrade.B, ItemGrade.A)),
                Arguments.of(4, 4, List.of(ItemGrade.B, ItemGrade.A, ItemGrade.S)),
                Arguments.of(20001, 10000, List.of()),
                Arguments.of(20001, 15000, List.of(ItemGrade.B)),
                Arguments.of(20001, 20000, List.of(ItemGrade.B, ItemGrade.A)),
                Arguments.of(Integer.MAX_VALUE, 1073741824, List.of(ItemGrade.B)),
                Arguments.of(Integer.MAX_VALUE, 1610612735, List.of(ItemGrade.B)),
                Arguments.of(Integer.MAX_VALUE, Integer.MAX_VALUE - 1, List.of(ItemGrade.B, ItemGrade.A)),
                Arguments.of(Integer.MAX_VALUE, Integer.MAX_VALUE, List.of(ItemGrade.B, ItemGrade.A, ItemGrade.S)));
    }

    @ParameterizedTest @MethodSource("thresholds")
    void usesRawCountsWithoutRoundingOrIntegerOverflow(int assigned, int completed, List<ItemGrade> expected) {
        assertThat(RewardPolicy.eligibleGrades(assigned, completed)).isEqualTo(expected);
    }

    @ParameterizedTest @CsvSource({"-1,0", "1,-1", "1,2", "0,1"})
    void rejectsInconsistentStoredCounts(int assigned, int completed) {
        assertThatThrownBy(() -> RewardPolicy.eligibleGrades(assigned, completed))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT));
    }

    @ParameterizedTest @CsvSource({"0,0,false", "3,0,false", "3,2,false", "3,3,true"})
    void emptyCatalogDoesNotMeanCollectionComplete(long active, long owned, boolean expected) {
        assertThat(RewardPolicy.allCollected(active, owned)).isEqualTo(expected);
    }

    @ParameterizedTest @CsvSource({"-1,0", "1,-1", "1,2"})
    void rejectsInconsistentCollectionCounts(long active, long owned) {
        assertThatThrownBy(() -> RewardPolicy.allCollected(active, owned)).isInstanceOf(BusinessException.class);
    }

    @Test void allowsInclusiveLeapYearButNot367DaysOrUnfinishedDates() {
        LocalDate today = LocalDate.of(2025, 1, 1);
        assertThatCode(() -> RewardPolicy.range(LocalDate.of(2024, 1, 1), today.minusDays(1), today)).doesNotThrowAnyException();
        for (var from : List.of(LocalDate.of(2023, 12, 31), today)) {
            assertThatThrownBy(() -> RewardPolicy.range(from, today.minusDays(1), today)).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> RewardPolicy.range(today, today, today)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> RewardPolicy.range(today, today.plusDays(1), today)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> RewardPolicy.range(null, today.minusDays(1), today)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest @CsvSource({"-1,20", "0,0", "0,101"})
    void rejectsInvalidPages(int page, int size) {
        assertThatThrownBy(() -> RewardPolicy.page(page, size)).isInstanceOf(BusinessException.class);
    }
}
