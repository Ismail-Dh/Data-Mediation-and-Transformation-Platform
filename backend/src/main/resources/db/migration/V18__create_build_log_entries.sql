-- V18__create_build_log_entries.sql
-- Tâche 5.4 : table dédiée au streaming SSE des logs de build Docker.
-- Distincte de build_logs (existant) pour ne pas casser le schéma existant.

CREATE TABLE build_log_entries (
    id              BIGSERIAL       PRIMARY KEY,
    pipeline_id     BIGINT          NOT NULL,
    version         VARCHAR(50),
    full_log        TEXT,
    status          VARCHAR(30)     NOT NULL,
    start_time      TIMESTAMP       NOT NULL,
    end_time        TIMESTAMP,
    docker_image_id BIGINT          NOT NULL,

    CONSTRAINT fk_ble_docker_image
        FOREIGN KEY (docker_image_id) REFERENCES docker_images(id)
);

CREATE INDEX idx_ble_pipeline_id    ON build_log_entries(pipeline_id);
CREATE INDEX idx_ble_start_time     ON build_log_entries(start_time DESC);