package com.swu.atch.report.service;

import com.swu.atch.session.domain.Session;
import com.swu.atch.session.domain.SessionEndReason;
import com.swu.atch.todo.domain.Todo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import com.swu.atch.todo.domain.*;
import com.swu.atch.user.domain.User;

class SessionTimeBucketsTest {
    @ParameterizedTest
    @CsvSource({"2026-10-29T14:59:59.700Z,2026-10-29T15:00:02.300Z", "2026-10-29T14:50:00Z,2026-10-29T15:20:00Z"})
    void fractionalMidnightAllocationPreservesStoredIntegerSeconds(String left,String right){
        Instant start=Instant.parse(left),end=Instant.parse(right),midnight=Instant.parse("2026-10-29T15:00:00Z");
        Session session=Session.start(todo(start),start);session.finish(end,SessionEndReason.COMPLETED);
        long before=SessionTimeBuckets.seconds(session,start,midnight,end),after=SessionTimeBuckets.seconds(session,midnight,end,end);
        assertThat(before+after).isEqualTo(session.getDurationSeconds().longValue());assertThat(before).isNotNegative();assertThat(after).isNotNegative();
    }
    @Test void openSessionIsCappedAtAsOfAndZeroLengthIsZero(){
        Instant start=Instant.parse("2026-10-29T14:50:00Z"),now=start.plusSeconds(20);
        Session session=Session.start(todo(start),start);
        assertThat(SessionTimeBuckets.seconds(session,start,start.plusSeconds(100),now)).isEqualTo(20);
        session.finish(start,SessionEndReason.PAUSED);
        assertThat(SessionTimeBuckets.seconds(session,start,start.plusSeconds(100),now)).isZero();
    }
    private Todo todo(Instant start){
        return Todo.builder().user(new User("시간분할")).title("집중").startAt(start).dueAt(start.plusSeconds(3600))
                .currentScheduleDate(start.atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate()).allDay(false)
                .userEstimatedDurationMinutes(30).userEstimateSource(UserEstimateSource.USER_INPUT)
                .selectedEstimateType(SelectedEstimateType.USER).build();
    }
}
