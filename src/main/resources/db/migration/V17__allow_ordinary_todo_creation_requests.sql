-- 일반 투두 생성도 사용자별 생성 요청 원장에 기록한다.
ALTER TABLE ai_creation_requests ALTER COLUMN draft_id DROP NOT NULL;
