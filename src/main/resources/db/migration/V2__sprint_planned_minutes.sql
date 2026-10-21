-- 스프린트 타임박스: 계획할 때 정한 공부 시간(분). 이전에 시작한 스프린트는 NULL(타임박스 없음)
ALTER TABLE sprint
    ADD COLUMN planned_minutes INT NULL AFTER started_at,
    ADD CONSTRAINT ck_sprint_planned_minutes CHECK (planned_minutes IS NULL OR planned_minutes BETWEEN 30 AND 720);
