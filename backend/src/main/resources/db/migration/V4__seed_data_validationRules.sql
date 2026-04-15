-- V4__seed_data_validationRules.sql
-- Règles de validation globales de base (Email, Phone, NotNull, Number)

-- Email
INSERT INTO validation_rules (field_name, rule_type, pattern, active, is_global, pipeline_id)
VALUES ('email', 'REGEX_EMAIL', NULL, TRUE, TRUE, 1)
ON CONFLICT DO NOTHING;

-- Phone
INSERT INTO validation_rules (field_name, rule_type, pattern, active, is_global, pipeline_id)
VALUES ('phone', 'REGEX_PHONE', NULL, TRUE, TRUE, 1)
ON CONFLICT DO NOTHING;

-- NotNull
INSERT INTO validation_rules (field_name, rule_type, pattern, active, is_global, pipeline_id)
VALUES ('required_field', 'NOT_NULL', NULL, TRUE, TRUE, 1)
ON CONFLICT DO NOTHING;

-- Number
INSERT INTO validation_rules (field_name, rule_type, pattern, active, is_global, pipeline_id)
VALUES ('number_field', 'TYPE_NUMBER', NULL, TRUE, TRUE, 1)
ON CONFLICT DO NOTHING;
