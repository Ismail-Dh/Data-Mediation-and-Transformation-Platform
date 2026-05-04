-- Add description column to validation_rules
ALTER TABLE validation_rules
ADD COLUMN description VARCHAR(500);