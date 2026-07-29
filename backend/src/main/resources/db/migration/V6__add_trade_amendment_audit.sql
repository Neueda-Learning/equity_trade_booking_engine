ALTER TABLE trades
    ADD COLUMN cancellation_reason VARCHAR(16) NULL;

ALTER TABLE trades
    ADD COLUMN supersedes_trade_id CHAR(36) NULL;

UPDATE trades
SET cancellation_reason = 'CANCELLED'
WHERE status = 'CANCELLED';

ALTER TABLE trades
    ADD CONSTRAINT chk_trades_cancellation_audit CHECK (
        (status = 'BOOKED'
            AND cancelled_at IS NULL
            AND cancellation_reason IS NULL)
        OR
        (status = 'CANCELLED'
            AND cancelled_at IS NOT NULL
            AND cancellation_reason IN ('CANCELLED', 'DELETED', 'AMENDED'))
    );

CREATE UNIQUE INDEX uk_trades_supersedes_trade
    ON trades (supersedes_trade_id);

ALTER TABLE trades
    ADD CONSTRAINT fk_trades_supersedes_trade
    FOREIGN KEY (supersedes_trade_id) REFERENCES trades (id);
