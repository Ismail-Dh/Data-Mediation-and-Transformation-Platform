-- V21add_docker_image_versioning.sql

ALTER TABLE docker_images
    ADD COLUMN version_patch         INT          NOT NULL DEFAULT 0,
    ADD COLUMN last_pipeline_version VARCHAR(50)  NULL;

COMMENT ON COLUMN docker_images.version_patch
    IS 'Patch auto-incrémenté à chaque build réussi. Repart à 0 si pipeline.version change.';

COMMENT ON COLUMN docker_images.last_pipeline_version
    IS 'Dernière pipeline.version utilisée lors du build, pour détecter un reset de patch.';