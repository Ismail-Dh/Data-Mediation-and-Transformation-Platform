-- V9__create_template_tables.sql
-- Templates lifecycle: DRAFT → PUBLISHED → DISABLED
-- Content stored as JSONB for flexible schema-less rule/mapping storage.

CREATE TABLE validation_templates (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200)             NOT NULL,
    description VARCHAR(500),
    content     JSONB                    NOT NULL,
    status      VARCHAR(20)              NOT NULL DEFAULT 'DRAFT',
    version     INT                      NOT NULL DEFAULT 1,
    parent_id   BIGINT,                               -- points to the PUBLISHED template that was forked
    created_by  BIGINT                   NOT NULL,
    created_at  TIMESTAMP                NOT NULL,
    updated_at  TIMESTAMP,
    CONSTRAINT fk_vt_user   FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_vt_parent FOREIGN KEY (parent_id)  REFERENCES validation_templates(id)
);

CREATE INDEX idx_vt_status     ON validation_templates(status);
CREATE INDEX idx_vt_created_by ON validation_templates(created_by);
CREATE INDEX idx_vt_parent_id  ON validation_templates(parent_id);

CREATE TABLE mapping_templates (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200)             NOT NULL,
    description VARCHAR(500),
    content     JSONB                    NOT NULL,
    status      VARCHAR(20)              NOT NULL DEFAULT 'DRAFT',
    version     INT                      NOT NULL DEFAULT 1,
    parent_id   BIGINT,
    created_by  BIGINT                   NOT NULL,
    created_at  TIMESTAMP                NOT NULL,
    updated_at  TIMESTAMP,
    CONSTRAINT fk_mt_user   FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_mt_parent FOREIGN KEY (parent_id)  REFERENCES mapping_templates(id)
);

CREATE INDEX idx_mt_status     ON mapping_templates(status);
CREATE INDEX idx_mt_created_by ON mapping_templates(created_by);
CREATE INDEX idx_mt_parent_id  ON mapping_templates(parent_id);