-- password_hash + rename display_name → username + UNIQUE

-- Переименование (идемпотентно через information_schema нельзя просто; ожидаем схему после 000001).
ALTER TABLE accounts
    CHANGE COLUMN display_name username VARCHAR(64) NOT NULL;

ALTER TABLE accounts
    ADD COLUMN password_hash VARCHAR(100) NOT NULL DEFAULT '$2a$10$invalid.placeholder.hash.value.........';

UPDATE accounts
SET password_hash = '$2a$10$invalid.placeholder.hash.value.........'
WHERE password_hash = '' OR password_hash IS NULL;

ALTER TABLE accounts
    ALTER COLUMN password_hash DROP DEFAULT;

-- Перед UNIQUE разводим дубликаты username (оставшаяся строка — самая ранняя).
UPDATE accounts a
    INNER JOIN (
        SELECT id
        FROM (
            SELECT id,
                   ROW_NUMBER() OVER (PARTITION BY username ORDER BY created_at ASC, id ASC) AS rn
            FROM accounts
        ) ranked
        WHERE ranked.rn > 1
    ) d ON a.id = d.id
SET a.username = CONCAT(LEFT(a.username, 40), '-', REPLACE(a.id, '-', ''));

ALTER TABLE accounts
    ADD UNIQUE KEY uq_accounts_username (username);
