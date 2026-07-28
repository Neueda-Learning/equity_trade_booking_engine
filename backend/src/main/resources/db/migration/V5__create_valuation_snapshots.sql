CREATE TABLE valuation_snapshots (
    id CHAR(36) PRIMARY KEY,
    scope_type VARCHAR(16) NOT NULL,
    account_id CHAR(36) NULL,
    total_cost_basis DECIMAL(25, 6) NOT NULL,
    total_market_value DECIMAL(25, 6) NOT NULL,
    unrealized_pnl DECIMAL(25, 6) NOT NULL,
    position_count INT NOT NULL,
    priced_position_count INT NOT NULL,
    complete BOOLEAN NOT NULL,
    mock BOOLEAN NOT NULL,
    stale BOOLEAN NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_valuation_snapshots_scope
        CHECK (
            (scope_type = 'ALL' AND account_id IS NULL)
            OR (scope_type = 'ACCOUNT' AND account_id IS NOT NULL)
        ),
    CONSTRAINT chk_valuation_snapshots_counts
        CHECK (
            position_count >= 0
            AND priced_position_count >= 0
            AND priced_position_count <= position_count
        ),
    CONSTRAINT fk_valuation_snapshots_account
        FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE INDEX idx_valuation_snapshots_scope_account_captured
    ON valuation_snapshots (scope_type, account_id, captured_at, id);
