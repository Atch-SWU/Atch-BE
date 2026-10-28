package com.swu.atch.ai.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class AiHistory {
    private final JdbcTemplate jdbc;

    public AiHistory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Observation(
            long todoId,
            String title,
            String sourceTaskName,
            String memo,
            int userMinutes,
            long actualSeconds,
            Instant completedAt) {
        public String sourceText() {
            return title + "\n" + AiJson.text(sourceTaskName) + "\n" + AiJson.text(memo);
        }

        public String sourceHash() {
            return AiJson.sha256(sourceText());
        }
    }

    // 실제 초를 먼저 합산한다. 열린 구간·미래 구간이 섞인 작업 전체를 관측에서 제외한다.
    public List<Observation> eligible(Long user, Instant asOf) {
        return jdbc.query(
                """
SELECT t.id,t.title,t.source_task_name,t.memo,t.user_estimated_duration_minutes,t.completed_at,
       sum(s.duration_seconds)::bigint actual_seconds
FROM todos t JOIN sessions s ON s.todo_id=t.id
WHERE t.user_id=? AND t.deleted_at IS NULL AND t.status='COMPLETED'
  AND t.user_estimate_source='USER_INPUT' AND t.user_estimated_duration_minutes>0
  AND t.completed_at<=? AND s.ended_at IS NOT NULL
  AND NOT EXISTS(SELECT 1 FROM sessions invalid WHERE invalid.todo_id=t.id
      AND (invalid.ended_at IS NULL OR invalid.ended_at>? OR invalid.started_at>invalid.ended_at))
GROUP BY t.id HAVING sum(s.duration_seconds)>0
ORDER BY t.completed_at DESC,t.id DESC
""",
                (rs, row) ->
                        new Observation(
                                rs.getLong("id"),
                                rs.getString("title"),
                                rs.getString("source_task_name"),
                                rs.getString("memo"),
                                rs.getInt("user_estimated_duration_minutes"),
                                rs.getLong("actual_seconds"),
                                rs.getTimestamp("completed_at").toInstant()),
                user,
                Timestamp.from(asOf),
                Timestamp.from(asOf));
    }
}
