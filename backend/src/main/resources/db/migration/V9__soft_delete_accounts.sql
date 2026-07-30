ALTER TABLE accounts
    ADD COLUMN deleted_at DATETIME(6) NULL;

CREATE INDEX idx_accounts_deleted_created
    ON accounts (deleted_at, created_at);
