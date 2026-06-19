CREATE TABLE sandbox_logs (
    id                 BIGSERIAL PRIMARY KEY,
    pipeline_id        BIGINT       NOT NULL REFERENCES pipelines(id),
    payload_id         BIGINT       REFERENCES payloads(id),
    validation_passed  BOOLEAN      NOT NULL,
    mapping_applied    BOOLEAN      NOT NULL,
    validation_message TEXT,
    duration_ms        BIGINT,
    executed_at        TIMESTAMP    NOT NULL,
    input_format       VARCHAR(20)
);

CREATE INDEX idx_sandbox_logs_pipeline ON sandbox_logs(pipeline_id);
CREATE INDEX idx_sandbox_logs_executed_at ON sandbox_logs(executed_at DESC);