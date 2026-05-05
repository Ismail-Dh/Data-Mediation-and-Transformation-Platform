-- V11__link_templates_to_pipeline.sql
-- Ajoute les colonnes FK pour attacher un ValidationTemplate
-- et un MappingTemplate optionnels à un pipeline.
--
-- Les deux colonnes sont nullable : un pipeline peut fonctionner
-- sans template (il utilise alors uniquement ses règles privées
-- et les GlobalValidationRules).

ALTER TABLE pipelines
    ADD COLUMN validation_template_id BIGINT,
    ADD COLUMN mapping_template_id    BIGINT;

ALTER TABLE pipelines
    ADD CONSTRAINT fk_pipeline_validation_template
        FOREIGN KEY (validation_template_id)
        REFERENCES validation_templates(id)
        ON DELETE SET NULL,
    ADD CONSTRAINT fk_pipeline_mapping_template
        FOREIGN KEY (mapping_template_id)
        REFERENCES mapping_templates(id)
        ON DELETE SET NULL;

CREATE INDEX idx_pipeline_vt ON pipelines(validation_template_id);
CREATE INDEX idx_pipeline_mt ON pipelines(mapping_template_id);