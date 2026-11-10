-- Alerting service schema. Source of truth: docs/02-ARCHITECTURE-PROPOSAL.md, section 5.
--
-- Portable SQL: this one script runs on H2 (PostgreSQL mode) and on PostgreSQL. It therefore
-- avoids partial or expression indexes, jsonb operators, SKIP LOCKED and arrays.
--
-- Conventions
--   * BIGINT keys come from one sequence per table with INCREMENT BY 50, matching Hibernate's pooled
--     optimizer (allocationSize = 50), so fan-out inserts can be JDBC-batched.
--     event_categories and channel_settings use natural keys; alert_rule_destinations is a join table.
--   * Timestamps are TIMESTAMP WITH TIME ZONE and always hold UTC instants.
--   * Enumerations are VARCHAR columns guarded by CHECK constraints and mapped as strings.
--   * JSON is stored as VARCHAR text and never queried with JSON operators.
--   * Rules and destinations are soft-deleted (deleted_at); version columns are optimistic locks.

-- ---------------------------------------------------------------------------------------------
-- Sequences
-- ---------------------------------------------------------------------------------------------
CREATE SEQUENCE users_seq                START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE event_sources_seq        START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE events_seq               START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE channel_destinations_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE alert_rules_seq          START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE notifications_seq        START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE delivery_attempts_seq    START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE audit_log_seq            START WITH 1 INCREMENT BY 50;

-- ---------------------------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------------------------
CREATE TABLE users (
    id           BIGINT                   NOT NULL,
    email        VARCHAR(320)             NOT NULL,
    display_name VARCHAR(100)             NOT NULL,
    role         VARCHAR(10)              NOT NULL,
    active       BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

-- ---------------------------------------------------------------------------------------------
-- Events: categories and sources (reference data), then the events themselves
-- ---------------------------------------------------------------------------------------------
CREATE TABLE event_categories (
    code         VARCHAR(30)  NOT NULL,
    display_name VARCHAR(80)  NOT NULL,
    description  VARCHAR(300),
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_event_categories PRIMARY KEY (code)
);

CREATE TABLE event_sources (
    id              BIGINT                   NOT NULL,
    code            VARCHAR(50)              NOT NULL,
    name            VARCHAR(100)             NOT NULL,
    type            VARCHAR(12)              NOT NULL,
    enabled         BOOLEAN                  NOT NULL DEFAULT TRUE,
    config          VARCHAR(2000)            NOT NULL DEFAULT '{}',
    last_success_at TIMESTAMP WITH TIME ZONE,
    last_error_at   TIMESTAMP WITH TIME ZONE,
    last_error      VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_event_sources PRIMARY KEY (id),
    CONSTRAINT uq_event_sources_code UNIQUE (code),
    CONSTRAINT ck_event_sources_type CHECK (type IN ('PUSH', 'POLL', 'SIMULATED'))
);

CREATE TABLE events (
    id                BIGINT                   NOT NULL,
    source_id         BIGINT                   NOT NULL,
    external_id       VARCHAR(200)             NOT NULL,
    category          VARCHAR(30)              NOT NULL,
    severity          SMALLINT                 NOT NULL,
    title             VARCHAR(300)             NOT NULL,
    summary           VARCHAR(2000),
    url               VARCHAR(2000),
    region            VARCHAR(50),
    occurred_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    attributes        VARCHAR(8000)            NOT NULL DEFAULT '{}',
    processing_status VARCHAR(16)              NOT NULL,
    matched_at        TIMESTAMP WITH TIME ZONE,
    match_count       INT                      NOT NULL DEFAULT 0,
    match_attempts    INT                      NOT NULL DEFAULT 0,
    match_error       VARCHAR(500),
    CONSTRAINT pk_events PRIMARY KEY (id),
    CONSTRAINT fk_events_source FOREIGN KEY (source_id) REFERENCES event_sources (id),
    CONSTRAINT fk_events_category FOREIGN KEY (category) REFERENCES event_categories (code),
    -- Event idempotency: the same source and external id is stored once.
    CONSTRAINT uq_events_source_external UNIQUE (source_id, external_id),
    CONSTRAINT ck_events_severity CHECK (severity BETWEEN 1 AND 5),
    CONSTRAINT ck_events_processing_status CHECK (processing_status IN ('RECEIVED', 'MATCHED', 'SKIPPED_STALE', 'FAILED'))
);
-- Unmatched-event sweeper
CREATE INDEX idx_events_status_received ON events (processing_status, received_at);
-- Admin event browser
CREATE INDEX idx_events_category_occurred ON events (category, occurred_at);

-- ---------------------------------------------------------------------------------------------
-- Destinations: where a user can be reached (an email address, a Slack webhook)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE channel_destinations (
    id              BIGINT                   NOT NULL,
    user_id         BIGINT                   NOT NULL,
    channel_type    VARCHAR(30)              NOT NULL,
    label           VARCHAR(100)             NOT NULL,
    config          VARCHAR(2000)            NOT NULL,
    destination_key VARCHAR(255)             NOT NULL,
    status          VARCHAR(10)              NOT NULL,
    deleted_at      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_channel_destinations PRIMARY KEY (id),
    CONSTRAINT fk_destinations_user FOREIGN KEY (user_id) REFERENCES users (id),
    -- Destination de-duplication per user and channel.
    CONSTRAINT uq_destinations_user_channel_key UNIQUE (user_id, channel_type, destination_key),
    CONSTRAINT ck_destinations_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
CREATE INDEX idx_destinations_user ON channel_destinations (user_id);

-- ---------------------------------------------------------------------------------------------
-- Alert rules and the destinations they use
-- ---------------------------------------------------------------------------------------------
CREATE TABLE alert_rules (
    id                BIGINT                   NOT NULL,
    user_id           BIGINT                   NOT NULL,
    name              VARCHAR(120)             NOT NULL,
    description       VARCHAR(500),
    category          VARCHAR(30)              NOT NULL,
    min_severity      SMALLINT                 NOT NULL DEFAULT 1,
    criteria          VARCHAR(4000)            NOT NULL DEFAULT '{}',
    cooldown_seconds  INT                      NOT NULL DEFAULT 0,
    enabled           BOOLEAN                  NOT NULL DEFAULT TRUE,
    last_triggered_at TIMESTAMP WITH TIME ZONE,
    deleted_at        TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    version           BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_alert_rules PRIMARY KEY (id),
    CONSTRAINT fk_rules_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_rules_category FOREIGN KEY (category) REFERENCES event_categories (code),
    CONSTRAINT ck_rules_min_severity CHECK (min_severity BETWEEN 1 AND 5),
    CONSTRAINT ck_rules_cooldown CHECK (cooldown_seconds BETWEEN 0 AND 86400)
);
-- Candidate rule query of the matcher
CREATE INDEX idx_rules_candidates ON alert_rules (category, enabled, min_severity);
CREATE INDEX idx_rules_user ON alert_rules (user_id);

CREATE TABLE alert_rule_destinations (
    rule_id        BIGINT NOT NULL,
    destination_id BIGINT NOT NULL,
    CONSTRAINT pk_alert_rule_destinations PRIMARY KEY (rule_id, destination_id),
    CONSTRAINT fk_rule_destinations_rule FOREIGN KEY (rule_id) REFERENCES alert_rules (id),
    CONSTRAINT fk_rule_destinations_destination FOREIGN KEY (destination_id) REFERENCES channel_destinations (id)
);
CREATE INDEX idx_rule_destinations_destination ON alert_rule_destinations (destination_id);

-- ---------------------------------------------------------------------------------------------
-- Notifications (the outbox) and the append-only ledger of delivery attempts
-- ---------------------------------------------------------------------------------------------
CREATE TABLE notifications (
    id                 BIGINT                   NOT NULL,
    event_id           BIGINT                   NOT NULL,
    alert_rule_id      BIGINT                   NOT NULL,
    destination_id     BIGINT                   NOT NULL,
    user_id            BIGINT                   NOT NULL,
    channel_type       VARCHAR(30)              NOT NULL,
    status             VARCHAR(16)              NOT NULL,
    status_reason      VARCHAR(60),
    attempt_count      INT                      NOT NULL DEFAULT 0,
    max_attempts       INT                      NOT NULL,
    next_attempt_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by          VARCHAR(100),
    locked_until       TIMESTAMP WITH TIME ZONE,
    last_error_code    VARCHAR(50),
    last_error_message VARCHAR(500),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at            TIMESTAMP WITH TIME ZONE,
    version            BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_notifications_rule FOREIGN KEY (alert_rule_id) REFERENCES alert_rules (id),
    CONSTRAINT fk_notifications_destination FOREIGN KEY (destination_id) REFERENCES channel_destinations (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    -- Notification idempotency: one row per event, rule and destination.
    CONSTRAINT uq_notifications_event_rule_destination UNIQUE (event_id, alert_rule_id, destination_id),
    CONSTRAINT ck_notifications_status CHECK (status IN
        ('PENDING', 'IN_FLIGHT', 'RETRY_SCHEDULED', 'SENT', 'DEAD', 'CANCELLED', 'SUPPRESSED'))
);
-- Dispatcher claim
CREATE INDEX idx_notifications_status_next ON notifications (status, next_attempt_at);
-- Lease sweeper
CREATE INDEX idx_notifications_status_locked ON notifications (status, locked_until);
-- User history
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at);
-- Admin filters and statistics
CREATE INDEX idx_notifications_channel_status ON notifications (channel_type, status);
CREATE INDEX idx_notifications_rule ON notifications (alert_rule_id);
CREATE INDEX idx_notifications_event ON notifications (event_id);

CREATE TABLE delivery_attempts (
    id                  BIGINT                   NOT NULL,
    notification_id     BIGINT                   NOT NULL,
    attempt_no          INT                      NOT NULL,
    started_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    outcome             VARCHAR(24)              NOT NULL,
    error_code          VARCHAR(50),
    error_message       VARCHAR(500),
    http_status         INT,
    latency_ms          INT                      NOT NULL,
    provider_message_id VARCHAR(200),
    worker_id           VARCHAR(100)             NOT NULL,
    CONSTRAINT pk_delivery_attempts PRIMARY KEY (id),
    CONSTRAINT fk_attempts_notification FOREIGN KEY (notification_id) REFERENCES notifications (id),
    -- Attempt ledger integrity: attempt numbers are unique per notification.
    CONSTRAINT uq_attempts_notification_no UNIQUE (notification_id, attempt_no),
    CONSTRAINT ck_attempts_outcome CHECK (outcome IN
        ('SUCCESS', 'RETRYABLE_FAILURE', 'PERMANENT_FAILURE', 'LEASE_EXPIRED'))
);

-- ---------------------------------------------------------------------------------------------
-- Channel settings (rows are created by the channel registry at start-up) and the admin audit log
-- ---------------------------------------------------------------------------------------------
CREATE TABLE channel_settings (
    channel_type VARCHAR(30)              NOT NULL,
    enabled      BOOLEAN                  NOT NULL DEFAULT TRUE,
    max_attempts INT,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by   BIGINT,
    CONSTRAINT pk_channel_settings PRIMARY KEY (channel_type),
    CONSTRAINT fk_channel_settings_user FOREIGN KEY (updated_by) REFERENCES users (id)
);

CREATE TABLE audit_log (
    id            BIGINT                   NOT NULL,
    actor_user_id BIGINT                   NOT NULL,
    action        VARCHAR(60)              NOT NULL,
    target_type   VARCHAR(40)              NOT NULL,
    target_id     VARCHAR(64)              NOT NULL,
    details       VARCHAR(2000),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
);
CREATE INDEX idx_audit_log_created ON audit_log (created_at);
