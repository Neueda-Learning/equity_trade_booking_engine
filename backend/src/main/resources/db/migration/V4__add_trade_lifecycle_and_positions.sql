ALTER TABLE trades DROP CONSTRAINT chk_trades_side;
ALTER TABLE trades DROP CONSTRAINT chk_trades_status;

ALTER TABLE trades
    ADD CONSTRAINT chk_trades_side CHECK (side IN ('BUY', 'SELL'));
ALTER TABLE trades
    ADD CONSTRAINT chk_trades_status
        CHECK (status IN ('BOOKED', 'CANCELLED'));

ALTER TABLE trades
    ADD COLUMN cancelled_at DATETIME(6) NULL;

CREATE INDEX idx_trades_position_replay
    ON trades (
        account_id,
        ticker,
        status,
        executed_at,
        created_at,
        id
    );
