
-- 신규 사용자 설정의 기본 활성화
ALTER TABLE user_settings
    ALTER COLUMN task_switch_restriction_enabled SET DEFAULT TRUE,
    ALTER COLUMN repeat_recommendation_enabled SET DEFAULT TRUE;
