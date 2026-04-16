CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL
);

CREATE TABLE providers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    endpoint VARCHAR(500) NOT NULL,
    protocol VARCHAR(50) NOT NULL,
    timeout INT NOT NULL
);

CREATE TABLE registries (
    id BIGSERIAL PRIMARY KEY,
    url VARCHAR(500) NOT NULL,
    username VARCHAR(100) NOT NULL,
    encrypted_password VARCHAR(255) NOT NULL
);

CREATE TABLE pipelines (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    provider_url VARCHAR(500),
    version VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    input_format VARCHAR(50) NOT NULL,
    output_format VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    user_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    CONSTRAINT fk_pipeline_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_pipeline_provider FOREIGN KEY(provider_id) REFERENCES providers(id)
);

CREATE TABLE docker_images (
    id BIGSERIAL PRIMARY KEY,
    image_name VARCHAR(300) NOT NULL,
    tag VARCHAR(100) NOT NULL,
    size_bytes BIGINT,
    status VARCHAR(50) NOT NULL,
    built_at TIMESTAMP,
    pipeline_id BIGINT NOT NULL UNIQUE,
    registry_id BIGINT,
    CONSTRAINT fk_dockerimage_pipeline FOREIGN KEY(pipeline_id) REFERENCES pipelines(id),
    CONSTRAINT fk_dockerimage_registry FOREIGN KEY(registry_id) REFERENCES registries(id)
);

CREATE TABLE build_logs (
    id BIGSERIAL PRIMARY KEY,
    log_content TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    success BOOLEAN NOT NULL,
    docker_image_id BIGINT NOT NULL,
    CONSTRAINT fk_buildlog_dockerimage FOREIGN KEY(docker_image_id) REFERENCES docker_images(id)
);

CREATE TABLE exchange_logs (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    message TEXT,
    http_status INT,
    error_detail TEXT,
    duration BIGINT NOT NULL,
    pipeline_id BIGINT NOT NULL,
    CONSTRAINT fk_exchangelog_pipeline FOREIGN KEY(pipeline_id) REFERENCES pipelines(id)
);

CREATE TABLE payloads (
    id BIGSERIAL PRIMARY KEY,
    raw_content TEXT NOT NULL,
    format VARCHAR(50) NOT NULL,
    received_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    pipeline_id BIGINT NOT NULL,
    exchange_log_id BIGINT UNIQUE,
    CONSTRAINT fk_payload_pipeline FOREIGN KEY(pipeline_id) REFERENCES pipelines(id),
    CONSTRAINT fk_payload_exchangelog FOREIGN KEY(exchange_log_id) REFERENCES exchange_logs(id)
);

CREATE TABLE provider_responses (
    id BIGSERIAL PRIMARY KEY,
    raw_content TEXT NOT NULL,
    http_status INT NOT NULL,
    received_at TIMESTAMP NOT NULL,
    success BOOLEAN NOT NULL,
    payload_id BIGINT NOT NULL UNIQUE,
    exchange_log_id BIGINT UNIQUE,
    CONSTRAINT fk_providerresponse_payload FOREIGN KEY(payload_id) REFERENCES payloads(id),
    CONSTRAINT fk_providerresponse_exchangelog FOREIGN KEY(exchange_log_id) REFERENCES exchange_logs(id)
);

CREATE TABLE mapping_rules (
    id BIGSERIAL PRIMARY KEY,
    source_field VARCHAR(200) NOT NULL,
    target_field VARCHAR(200) NOT NULL,
    mapping_type VARCHAR(50) NOT NULL,
    expression TEXT,
    active BOOLEAN NOT NULL,
    pipeline_id BIGINT NOT NULL,
    CONSTRAINT fk_mappingrule_pipeline FOREIGN KEY(pipeline_id) REFERENCES pipelines(id)
);

CREATE TABLE validation_rules (
    id BIGSERIAL PRIMARY KEY,
    field_name VARCHAR(150) NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    pattern VARCHAR(500),
    active BOOLEAN NOT NULL,
    is_global BOOLEAN NOT NULL,
    pipeline_id BIGINT NOT NULL,
    CONSTRAINT fk_validationrule_pipeline FOREIGN KEY(pipeline_id) REFERENCES pipelines(id)
);
