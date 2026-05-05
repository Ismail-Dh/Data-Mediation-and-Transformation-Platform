-- V10__create_audit_log_table.sql
CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    admin_username VARCHAR(150) NOT NULL,
    action         VARCHAR(100) NOT NULL,
    target_entity  VARCHAR(100) NOT NULL,
    target_id      VARCHAR(100),
    details        TEXT,
    timestamp      TIMESTAMP    NOT NULL
);