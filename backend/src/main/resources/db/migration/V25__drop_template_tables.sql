-- V25__drop_template_tables.sql
-- Supprime le système de templates (ValidationTemplate / MappingTemplate)
-- introduit par V10, V11 et V12, désormais retiré du code applicatif.
--
-- Ordre : on retire d'abord les FK/colonnes sur pipelines (V11),
-- puis les tables templates elles-mêmes (V10, dont le contenu incluait
-- déjà les données de seed insérées par V12).

-- --- Retrait du lien pipelines -> templates (ex V11) ---

ALTER TABLE pipelines
    DROP CONSTRAINT IF EXISTS fk_pipeline_validation_template,
    DROP CONSTRAINT IF EXISTS fk_pipeline_mapping_template;

DROP INDEX IF EXISTS idx_pipeline_vt;
DROP INDEX IF EXISTS idx_pipeline_mt;

ALTER TABLE pipelines
    DROP COLUMN IF EXISTS validation_template_id,
    DROP COLUMN IF EXISTS mapping_template_id;

-- --- Suppression des tables templates (ex V10 / données de V12) ---

DROP INDEX IF EXISTS idx_vt_status;
DROP INDEX IF EXISTS idx_vt_created_by;
DROP INDEX IF EXISTS idx_vt_parent_id;
DROP TABLE IF EXISTS validation_templates;

DROP INDEX IF EXISTS idx_mt_status;
DROP INDEX IF EXISTS idx_mt_created_by;
DROP INDEX IF EXISTS idx_mt_parent_id;
DROP TABLE IF EXISTS mapping_templates;