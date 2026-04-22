-- V5__add_email_to_users.sql
-- Ajout du champ email à la table users
ALTER TABLE users
    ADD COLUMN email VARCHAR(255) UNIQUE,
    ADD COLUMN reset_code          VARCHAR(6),
    ADD COLUMN reset_code_expiration TIMESTAMP;
