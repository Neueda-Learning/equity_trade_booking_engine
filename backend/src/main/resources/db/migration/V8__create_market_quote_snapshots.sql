CREATE TABLE market_quote_snapshots (
    id CHAR(36) PRIMARY KEY,
    ticker VARCHAR(32) NOT NULL,
    price DECIMAL(25, 6) NOT NULL,
    previous_close DECIMAL(25, 6) NOT NULL,
    market_timestamp DATETIME(6) NOT NULL,
    fetched_at DATETIME(6) NOT NULL,
    source VARCHAR(32) NOT NULL,
    mock BOOLEAN NOT NULL,
    persisted_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_market_quote_snapshots_prices
        CHECK (price > 0 AND previous_close > 0)
);

CREATE INDEX idx_market_quote_snapshots_ticker_fetched
    ON market_quote_snapshots (ticker, fetched_at, id);

CREATE INDEX idx_market_quote_snapshots_persisted
    ON market_quote_snapshots (persisted_at, id);
