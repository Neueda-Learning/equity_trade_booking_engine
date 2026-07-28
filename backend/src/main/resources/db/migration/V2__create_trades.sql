CREATE TABLE trades (
    id CHAR(36) PRIMARY KEY,
    ticker VARCHAR(10) NOT NULL,
    side VARCHAR(8) NOT NULL,
    quantity DECIMAL(19, 6) NOT NULL,
    trade_price DECIMAL(19, 6) NOT NULL,
    executed_at DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_trades_side CHECK (side = 'BUY'),
    CONSTRAINT chk_trades_status CHECK (status = 'BOOKED'),
    CONSTRAINT chk_trades_quantity CHECK (quantity > 0),
    CONSTRAINT chk_trades_trade_price CHECK (trade_price > 0)
);

CREATE INDEX idx_trades_executed_at ON trades (executed_at);
CREATE INDEX idx_trades_ticker ON trades (ticker);
