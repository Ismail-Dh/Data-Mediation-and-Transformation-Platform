-- V2__seed_data_admin_provider.sql
-- Données d'initialisation indispensables au démarrage
-- Le hash BCrypt ci-dessous correspond au mot de passe : Admin1234!

INSERT INTO users (username, password_hash, role)
VALUES ('admin', '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'ADMIN')
ON CONFLICT (username) DO NOTHING;

-- 1a. Provider par défaut pour le pipeline
INSERT INTO providers (name, endpoint, protocol, timeout)
VALUES ('Default Provider', 'http://localhost', 'HTTP', 30)
ON CONFLICT DO NOTHING;