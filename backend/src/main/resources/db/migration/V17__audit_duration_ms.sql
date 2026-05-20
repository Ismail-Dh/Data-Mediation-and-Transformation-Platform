-- V17__audit_duration_ms.sql
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS duration_ms BIGINT;