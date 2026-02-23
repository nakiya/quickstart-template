CREATE TABLE IF NOT EXISTS system (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    initialized INTEGER NOT NULL DEFAULT 0);
--;;
INSERT OR IGNORE INTO system (id, initialized)
    VALUES (1, 0);
--;;
CREATE TABLE IF NOT EXISTS accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    role TEXT NOT NULL
      CHECK (role IN ('admin', 'cashier', 'manager',
                      'inventory_clerk', 'accountant')),
    status TEXT NOT NULL DEFAULT 'active'
      CHECK (status IN ('active', 'disabled')),
    created_at TEXT NOT NULL,
    created_by INTEGER REFERENCES accounts(id));
