-- 기존 패턴·규칙·Todo와 회차 식별자는 보존한다. 하루종일은 시각 없는 별도 패턴이다.
ALTER TABLE repeat_patterns
    ADD COLUMN all_day BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN detection_after_todo_id BIGINT NOT NULL DEFAULT 0,
    ALTER COLUMN scheduled_time DROP NOT NULL;
ALTER TABLE repeat_patterns DROP CONSTRAINT ck_repeat_patterns_occurrence_count;
ALTER TABLE repeat_patterns
    ADD CONSTRAINT ck_repeat_patterns_occurrence_count CHECK (occurrence_count >= 0),
    ADD CONSTRAINT ck_repeat_patterns_detection_boundary CHECK (detection_after_todo_id >= 0),
    ADD CONSTRAINT ck_repeat_patterns_schedule CHECK (
        (all_day AND scheduled_time IS NULL) OR (NOT all_day AND scheduled_time IS NOT NULL));

ALTER TABLE recurrence_rules
    ADD COLUMN all_day BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN generation_not_before TIMESTAMPTZ,
    ALTER COLUMN scheduled_time DROP NOT NULL;
-- 과거 활성화 이력은 역산하지 않고 마지막 수정 시각을 보수적 기준으로 삼는다.
UPDATE recurrence_rules SET generation_not_before = updated_at;
ALTER TABLE recurrence_rules
    ALTER COLUMN generation_not_before SET NOT NULL,
    ALTER COLUMN generation_not_before SET DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT ck_recurrence_rules_schedule CHECK (
        (all_day AND scheduled_time IS NULL AND default_deadline_offset_minutes = 1440)
        OR (NOT all_day AND scheduled_time IS NOT NULL));

-- 삭제 전 자료로 즉시 재추천하지 않는다. 원본 상태와 관측 횟수는 보존한다.
UPDATE repeat_patterns p
SET detection_after_todo_id = GREATEST(p.detection_after_todo_id,
    COALESCE((SELECT max(t.id) FROM todos t JOIN recurrence_rules r ON r.user_id=t.user_id
        WHERE r.source_pattern_id=p.id AND r.deleted_at IS NOT NULL AND t.created_at<=r.deleted_at), 0));
CREATE INDEX ix_repeat_patterns_user_identity
    ON repeat_patterns(user_id, task_name, all_day, scheduled_time, frequency, day_of_week);
CREATE INDEX ix_todos_recurrence_observation
    ON todos(user_id, start_at, id) WHERE recurrence_rule_id IS NULL AND deleted_at IS NULL;
