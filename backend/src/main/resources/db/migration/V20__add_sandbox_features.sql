ALTER TABLE sandbox_logs ADD COLUMN raw_content TEXT;
ALTER TABLE sandbox_logs ADD COLUMN failure_step VARCHAR(20);
ALTER TABLE sandbox_logs ADD COLUMN violations TEXT;
ALTER TABLE sandbox_logs ADD COLUMN original_payload TEXT;
ALTER TABLE sandbox_logs ADD COLUMN mapped_payload TEXT;
ALTER TABLE sandbox_logs ADD COLUMN mapping_summary TEXT;