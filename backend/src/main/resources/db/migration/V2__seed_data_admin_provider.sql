-- V2__seed_data_admin_provider.sql
-- Données d'initialisation indispensables au démarrage
-- Le hash BCrypt ci-dessous correspond au mot de passe : Admin1234!

INSERT INTO users (username, password_hash, role)
VALUES ('admin', '$2a$10$SJu1DfU8kJmzgG9.pYjG6u7MmO1Jq8sXfi9.MDVJGofE7RmGrr9je', 'ADMIN')
ON CONFLICT (username) DO NOTHING;

-- 1a. Provider par défaut pour le pipeline
INSERT INTO providers (name, endpoint, protocol, timeout)
VALUES ('Default Provider', 'http://localhost', 'HTTP', 30)
ON CONFLICT DO NOTHING;