ALTER TABLE accounts DROP INDEX uq_accounts_username;
ALTER TABLE accounts DROP COLUMN password_hash;
ALTER TABLE accounts CHANGE COLUMN username display_name VARCHAR(64) NOT NULL;
