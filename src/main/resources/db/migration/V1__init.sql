CREATE TABLE guest
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    public_id    CHAR(36)    NOT NULL,
    created_at   DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_guest_public_id UNIQUE (public_id)
);

CREATE TABLE guest_setting
(
    guest_id            BIGINT  NOT NULL,
    break_alert_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    break_alert_minutes INT     NOT NULL DEFAULT 50,
    PRIMARY KEY (guest_id),
    CONSTRAINT fk_guest_setting_guest FOREIGN KEY (guest_id) REFERENCES guest (id),
    CONSTRAINT ck_guest_setting_minutes CHECK (break_alert_minutes > 0)
);

CREATE TABLE sprint
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    guest_id      BIGINT      NOT NULL,
    status        VARCHAR(10) NOT NULL,
    -- OPEN일 때만 값이 차므로 UNIQUE로 게스트당 열린 스프린트 1개를 보장한다
    open_guest_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'OPEN' THEN guest_id END) STORED,
    started_at    DATETIME(3) NOT NULL,
    closed_at     DATETIME(3) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sprint_guest FOREIGN KEY (guest_id) REFERENCES guest (id),
    CONSTRAINT uk_sprint_open_guest_id UNIQUE (open_guest_id),
    CONSTRAINT ck_sprint_status CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX idx_sprint_guest_started ON sprint (guest_id, started_at);

CREATE TABLE task
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    sprint_id            BIGINT       NOT NULL,
    title                VARCHAR(100) NOT NULL,
    estimated_minutes    INT          NULL,
    status               VARCHAR(10)  NOT NULL,
    carried_from_task_id BIGINT       NULL,
    root_task_id         BIGINT       NULL,
    sort_order           INT          NOT NULL,
    created_at           DATETIME(3)  NOT NULL,
    completed_at         DATETIME(3)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_sprint FOREIGN KEY (sprint_id) REFERENCES sprint (id),
    CONSTRAINT fk_task_carried_from FOREIGN KEY (carried_from_task_id) REFERENCES task (id),
    CONSTRAINT fk_task_root FOREIGN KEY (root_task_id) REFERENCES task (id),
    CONSTRAINT uk_task_carried_from UNIQUE (carried_from_task_id),
    CONSTRAINT ck_task_estimated_minutes CHECK (estimated_minutes > 0),
    CONSTRAINT ck_task_status CHECK (status IN ('TODO', 'DONE', 'CARRIED', 'DROPPED'))
);

CREATE INDEX idx_task_sprint_sort ON task (sprint_id, sort_order);
CREATE INDEX idx_task_root ON task (root_task_id);

CREATE TABLE focus_session
(
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    task_id           BIGINT      NOT NULL,
    started_at        DATETIME(3) NOT NULL,
    ended_at          DATETIME(3) NULL,
    last_heartbeat_at DATETIME(3) NOT NULL,
    duration_seconds  INT         NULL,
    end_reason        VARCHAR(20) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_focus_session_task FOREIGN KEY (task_id) REFERENCES task (id),
    CONSTRAINT ck_focus_session_end_reason CHECK (end_reason IN
        ('STOPPED', 'COMPLETED', 'SWITCHED', 'BREAK', 'DISCONNECTED', 'SPRINT_CLOSED'))
);

CREATE INDEX idx_focus_session_task ON focus_session (task_id);
CREATE INDEX idx_focus_session_open ON focus_session (ended_at, last_heartbeat_at);
