-- V3__seed_data.sql

INSERT INTO pipelines (
	name, provider_url, version, created_at, input_format, output_format, status, user_id, provider_id
) VALUES (
	'Global Validation Pipeline', NULL, NULL, NOW(), 'JSON', 'JSON', 'DRAFT', 1, 1
)
ON CONFLICT DO NOTHING;


