-- =============================================================================
--  V1 — Initial schema for the Callback System
-- =============================================================================

-- Target configuration --------------------------------------------------------
CREATE TABLE callback_targets (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                     VARCHAR(255) NOT NULL,
    url                      VARCHAR(2048) NOT NULL,
    http_method              VARCHAR(10)  NOT NULL DEFAULT 'POST',
    headers                  JSONB,
    auth_type                VARCHAR(50)  NOT NULL DEFAULT 'NONE',
    auth_config              JSONB,                   -- store encrypted in prod
    max_retries              INT          NOT NULL DEFAULT 3,
    retry_base_delay_seconds BIGINT       NOT NULL DEFAULT 60,
    retry_backoff_multiplier NUMERIC(6,3) NOT NULL DEFAULT 2.0,
    timeout_seconds          INT          NOT NULL DEFAULT 30,
    enabled                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                  BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_callback_targets_name UNIQUE (name)
);

-- Execution tracking ----------------------------------------------------------
CREATE TABLE callback_executions (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    callback_type VARCHAR(255) NOT NULL,
    data          JSONB        NOT NULL,
    target_id     UUID         NOT NULL REFERENCES callback_targets (id),
    status        VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    attempt_count INT          NOT NULL DEFAULT 0,
    max_attempts  INT          NOT NULL,
    next_retry_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0
);

-- Indexes for the hot query paths
CREATE INDEX idx_executions_status
    ON callback_executions (status);

CREATE INDEX idx_executions_retry_scheduled
    ON callback_executions (next_retry_at)
    WHERE status = 'RETRY_SCHEDULED';

CREATE INDEX idx_executions_stuck_pending
    ON callback_executions (created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_executions_target_id
    ON callback_executions (target_id);

-- Attempt audit log -----------------------------------------------------------
CREATE TABLE callback_attempts (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id        UUID        NOT NULL REFERENCES callback_executions (id),
    attempt_number      INT         NOT NULL,
    success             BOOLEAN     NOT NULL,
    response_status_code INT,
    response_body       TEXT,
    error_message       TEXT,
    duration_ms         BIGINT,
    attempted_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_attempts_execution_id
    ON callback_attempts (execution_id);
