-- V16__create_pipeline_fields_table.sql

CREATE TABLE pipeline_fields (
    id          BIGSERIAL PRIMARY KEY,
    field_path  VARCHAR(200) NOT NULL,
    field_type  VARCHAR(30)  NOT NULL,
    required    BOOLEAN      NOT NULL,
    nullable    BOOLEAN      NOT NULL,
    pipeline_id BIGINT       NOT NULL,

    CONSTRAINT fk_pipeline_fields_pipeline
        FOREIGN KEY (pipeline_id)
        REFERENCES pipelines(id)
        ON DELETE CASCADE
);