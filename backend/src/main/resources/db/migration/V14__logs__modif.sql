ALTER TABLE audit_logs RENAME COLUMN admin_username TO performed_by;
ALTER TABLE audit_logs ADD COLUMN performed_by_role VARCHAR(50);