-- 미입력은 0분이 아니라 예상·출처·선택·AI 제안이 모두 없는 상태다.
ALTER TABLE todos
    ALTER COLUMN user_estimated_duration_minutes DROP NOT NULL,
    ALTER COLUMN user_estimate_source DROP NOT NULL,
    ALTER COLUMN selected_estimate_type DROP NOT NULL,
    ADD CONSTRAINT ck_todos_estimate_presence CHECK (
        (user_estimated_duration_minutes IS NULL
            AND user_estimate_source IS NULL
            AND ai_estimated_duration_minutes IS NULL
            AND selected_estimate_type IS NULL)
        OR
        (user_estimated_duration_minutes IS NOT NULL
            AND user_estimate_source IS NOT NULL
            AND selected_estimate_type IS NOT NULL)
    );

ALTER TABLE recurrence_rules
    ALTER COLUMN default_user_estimated_duration_minutes DROP NOT NULL;
