-- Demo users for the local profile only (loaded from db/dev, never from db/migration).
-- Repeatable and idempotent: it can run again at any time without creating duplicates.
-- The addresses use example.com, which is reserved and never delivers real mail.

INSERT INTO users (id, email, display_name, role, active, created_at, updated_at)
SELECT NEXTVAL('users_seq'), 'admin@example.com', 'Demo Admin', 'ADMIN', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@example.com');

INSERT INTO users (id, email, display_name, role, active, created_at, updated_at)
SELECT NEXTVAL('users_seq'), 'alice@example.com', 'Alice Demo', 'USER', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'alice@example.com');

INSERT INTO users (id, email, display_name, role, active, created_at, updated_at)
SELECT NEXTVAL('users_seq'), 'bob@example.com', 'Bob Demo', 'USER', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'bob@example.com');
