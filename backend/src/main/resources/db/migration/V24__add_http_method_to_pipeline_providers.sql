-- V24__add_http_method_to_pipeline_providers.sql
-- Provider Request Method Flexibility
--
-- La méthode HTTP (GET, POST, PUT, PATCH) utilisée pour transmettre le payload
-- mappé à un provider est configurée au niveau de l'association pipeline↔provider
-- (table pipeline_providers), et non sur le provider lui-même : un même provider
-- peut être appelé différemment selon le pipeline qui l'utilise, et la méthode
-- se choisit lors de la création/édition du pipeline (pas dans l'écran de
-- gestion des providers).

ALTER TABLE pipeline_providers
    ADD COLUMN http_method VARCHAR(10) NOT NULL DEFAULT 'POST';

ALTER TABLE pipeline_providers
    ADD CONSTRAINT chk_pipeline_providers_http_method
    CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH'));

COMMENT ON COLUMN pipeline_providers.http_method IS
    'Méthode HTTP utilisée par ce pipeline pour transmettre le payload mappé à ce provider.';