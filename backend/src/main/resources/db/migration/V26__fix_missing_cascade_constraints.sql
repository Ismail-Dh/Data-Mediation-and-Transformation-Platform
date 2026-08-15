-- V26__fix_missing_cascade_constraints.sql
--
-- Corrige plusieurs contraintes FK qui bloquaient la suppression de
-- pipelines / payloads / providers / registries avec une
-- DataIntegrityViolationException (409), car les entités enfants n'étaient
-- rattachées nulle part :
--
--   1. sandbox_logs.pipeline_id       → pipelines(id)   : pas de ON DELETE
--   2. sandbox_logs.payload_id        → payloads(id)    : pas de ON DELETE
--   3. build_log_entries.docker_image_id → docker_images(id) : pas de ON DELETE
--   4. docker_images.registry_id      → registries(id)  : pas de ON DELETE
--   5. provider_responses.provider_id → providers(id)   : pas de ON DELETE
--   6. pipelines.provider_id (colonne dépréciée) → providers(id) : pas de ON DELETE
--
-- Pour 1-3 (tables de log/historique dont le parent EST le propriétaire du
-- cycle de vie) : ON DELETE CASCADE — supprimer le parent doit supprimer
-- son historique de logs.
--
-- Pour 4-6 (références vers un provider/registry qui peut être supprimé
-- indépendamment de l'historique qui le mentionne) : ON DELETE SET NULL —
-- supprimer un provider/registry ne doit PAS supprimer les données déjà
-- persistées (réponses, images), seulement détacher la référence.

-- ── sandbox_logs ────────────────────────────────────────────────────────
ALTER TABLE sandbox_logs
    DROP CONSTRAINT IF EXISTS sandbox_logs_pipeline_id_fkey,
    ADD CONSTRAINT sandbox_logs_pipeline_id_fkey
        FOREIGN KEY (pipeline_id) REFERENCES pipelines(id) ON DELETE CASCADE;

ALTER TABLE sandbox_logs
    DROP CONSTRAINT IF EXISTS sandbox_logs_payload_id_fkey,
    ADD CONSTRAINT sandbox_logs_payload_id_fkey
        FOREIGN KEY (payload_id) REFERENCES payloads(id) ON DELETE CASCADE;

-- ── build_log_entries ───────────────────────────────────────────────────
ALTER TABLE build_log_entries
    DROP CONSTRAINT IF EXISTS fk_ble_docker_image,
    ADD CONSTRAINT fk_ble_docker_image
        FOREIGN KEY (docker_image_id) REFERENCES docker_images(id) ON DELETE CASCADE;

-- ── docker_images.registry_id ───────────────────────────────────────────
ALTER TABLE docker_images
    DROP CONSTRAINT IF EXISTS fk_dockerimage_registry,
    ADD CONSTRAINT fk_dockerimage_registry
        FOREIGN KEY (registry_id) REFERENCES registries(id) ON DELETE SET NULL;

-- ── provider_responses.provider_id ──────────────────────────────────────
ALTER TABLE provider_responses
    DROP CONSTRAINT IF EXISTS provider_responses_provider_id_fkey,
    ADD CONSTRAINT provider_responses_provider_id_fkey
        FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE SET NULL;

-- ── pipelines.provider_id (colonne dépréciée depuis V23, non utilisée par
--    le code Java, mais toujours contrainte en base — bloquerait sinon la
--    suppression d'un provider légacy encore référencé) ──────────────────
ALTER TABLE pipelines
    DROP CONSTRAINT IF EXISTS fk_pipeline_provider,
    ADD CONSTRAINT fk_pipeline_provider
        FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE SET NULL;

