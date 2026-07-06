-- V23__multi_provider_and_response_mapping.sql
-- T5/T6/T7 — Un pipeline peut être lié à plusieurs providers.
-- Chaque ProviderResponse appartient désormais à un (payload, provider) et
-- non plus uniquement à un payload, puisqu'un même payload peut recevoir
-- une réponse par provider appelé.
-- Ajoute également la table des règles de validation/mapping appliquées
-- aux réponses provider (T6).

-- ─────────────────────────────────────────────────────────────────────────
-- 1. Table de jointure pipeline ↔ provider (remplace pipelines.provider_id)
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE pipeline_providers (
    pipeline_id BIGINT NOT NULL REFERENCES pipelines(id)  ON DELETE CASCADE,
    provider_id BIGINT NOT NULL REFERENCES providers(id)  ON DELETE CASCADE,
    PRIMARY KEY (pipeline_id, provider_id)
);

CREATE INDEX idx_pipeline_providers_pipeline ON pipeline_providers(pipeline_id);
CREATE INDEX idx_pipeline_providers_provider ON pipeline_providers(provider_id);

-- Migrer les données existantes : chaque pipeline.provider_id devient une ligne
INSERT INTO pipeline_providers (pipeline_id, provider_id)
SELECT id, provider_id FROM pipelines WHERE provider_id IS NOT NULL;

-- L'ancienne colonne est conservée mais nullable et non utilisée par le code Java
-- (dépréciée — supprimée dans une migration future une fois la transition validée)
COMMENT ON COLUMN pipelines.provider_id IS 'DEPRECATED — remplacé par pipeline_providers. Conservé pour rollback.';

-- ─────────────────────────────────────────────────────────────────────────
-- 2. provider_responses : ajout du lien vers le provider concerné
--    (un payload peut désormais avoir N réponses, une par provider)
-- ─────────────────────────────────────────────────────────────────────────
ALTER TABLE provider_responses
    ADD COLUMN provider_id BIGINT REFERENCES providers(id);

ALTER TABLE provider_responses
    ADD COLUMN duration_ms BIGINT;

-- Supprime la contrainte unique sur payload_id (un payload peut avoir
-- plusieurs ProviderResponse désormais — une par provider)
ALTER TABLE provider_responses
    DROP CONSTRAINT IF EXISTS provider_responses_payload_id_key;

CREATE INDEX idx_provider_responses_payload  ON provider_responses(payload_id);
CREATE INDEX idx_provider_responses_provider ON provider_responses(provider_id);

-- ─────────────────────────────────────────────────────────────────────────
-- 3. response_mapping_rules — règles de validation/transformation
--    appliquées aux réponses des providers (T6)
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE response_mapping_rules (
    id              BIGSERIAL PRIMARY KEY,
    pipeline_id     BIGINT       NOT NULL REFERENCES pipelines(id) ON DELETE CASCADE,
    provider_id     BIGINT       REFERENCES providers(id) ON DELETE CASCADE,
    source_field    VARCHAR(200) NOT NULL,
    target_field    VARCHAR(200) NOT NULL,
    mapping_type    VARCHAR(50)  NOT NULL,
    expression      TEXT,
    required        BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_response_mapping_rules_pipeline ON response_mapping_rules(pipeline_id);
CREATE INDEX idx_response_mapping_rules_provider ON response_mapping_rules(provider_id);

-- ─────────────────────────────────────────────────────────────────────────
-- 4. consumer_responses — résultat agrégé final renvoyé au consommateur (T7)
--    Persisté pour traçabilité et pour que le frontend (T10) puisse
--    afficher l'historique des validations/transformations de réponses.
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE consumer_responses (
    id                BIGSERIAL PRIMARY KEY,
    payload_id        BIGINT      NOT NULL REFERENCES payloads(id) ON DELETE CASCADE,
    pipeline_id       BIGINT      NOT NULL REFERENCES pipelines(id) ON DELETE CASCADE,
    built_at          TIMESTAMP   NOT NULL,
    aggregated_body   TEXT        NOT NULL,
    overall_success   BOOLEAN     NOT NULL,
    provider_count    INT         NOT NULL DEFAULT 0,
    success_count     INT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_consumer_responses_payload  ON consumer_responses(payload_id);
CREATE INDEX idx_consumer_responses_pipeline ON consumer_responses(pipeline_id);