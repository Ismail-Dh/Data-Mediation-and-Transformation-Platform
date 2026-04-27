-- Migration : rendre pipeline_id nullable dans validation_rules
-- Les règles globales (Admin) ne sont pas rattachées à un pipeline

ALTER TABLE validation_rules
    ALTER COLUMN pipeline_id DROP NOT NULL;