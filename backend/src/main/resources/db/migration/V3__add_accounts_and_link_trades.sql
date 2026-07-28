CREATE TABLE accounts (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    broker VARCHAR(100) NOT NULL,
    account_number_last4 VARCHAR(4) NULL,
    base_currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_accounts_name UNIQUE (name),
    CONSTRAINT chk_accounts_last4 CHECK (
        account_number_last4 IS NULL
        OR account_number_last4 REGEXP '^[0-9]{4}$'
    ),
    CONSTRAINT chk_accounts_base_currency CHECK (base_currency = 'USD'),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

INSERT INTO accounts (
    id,
    name,
    broker,
    account_number_last4,
    base_currency,
    status,
    created_at,
    updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Primary Account',
    'Legacy',
    NULL,
    'USD',
    'ACTIVE',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
);

ALTER TABLE trades ADD COLUMN account_id CHAR(36) NULL;

UPDATE trades
SET account_id = '00000000-0000-0000-0000-000000000001'
WHERE account_id IS NULL;

ALTER TABLE trades MODIFY account_id CHAR(36) NOT NULL;

CREATE INDEX idx_trades_account_id ON trades (account_id);

ALTER TABLE trades
    ADD CONSTRAINT fk_trades_account
    FOREIGN KEY (account_id) REFERENCES accounts (id);
