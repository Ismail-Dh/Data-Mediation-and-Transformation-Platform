-- Table registries
ALTER TABLE registries ADD COLUMN IF NOT EXISTS name VARCHAR(150) NOT NULL DEFAULT 'default';
