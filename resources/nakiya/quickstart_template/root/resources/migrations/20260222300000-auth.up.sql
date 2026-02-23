CREATE TABLE IF NOT EXISTS credentials (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL UNIQUE
      REFERENCES accounts(id),
    password_hash TEXT NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TEXT);
--;;
CREATE TABLE IF NOT EXISTS sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL
      REFERENCES accounts(id),
    token TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'active'
      CHECK (status IN ('active', 'expired', 'revoked')),
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL);
