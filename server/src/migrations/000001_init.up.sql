CREATE TABLE accounts (
    id CHAR(36) NOT NULL PRIMARY KEY,
    display_name VARCHAR(64) NOT NULL,
    avatar_id INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE auth_tokens (
    token_hash CHAR(64) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_auth_tokens_account
        FOREIGN KEY (account_id) REFERENCES accounts (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_auth_tokens_account ON auth_tokens (account_id);

CREATE TABLE games (
    id CHAR(36) NOT NULL PRIMARY KEY,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NOT NULL,
    access_code VARCHAR(16) NULL,
    players_at_start INT NOT NULL,
    result VARCHAR(16) NOT NULL,
    fool_account_id CHAR(36) NULL,
    CONSTRAINT fk_games_fool
        FOREIGN KEY (fool_account_id) REFERENCES accounts (id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE game_players (
    game_id CHAR(36) NOT NULL,
    account_id CHAR(36) NOT NULL,
    player_result VARCHAR(16) NOT NULL,
    PRIMARY KEY (game_id, account_id),
    CONSTRAINT fk_gp_game
        FOREIGN KEY (game_id) REFERENCES games (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_gp_account
        FOREIGN KEY (account_id) REFERENCES accounts (id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
