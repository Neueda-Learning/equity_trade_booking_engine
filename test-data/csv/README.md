# CSV import test data

These files use the application's exact import columns:

`account,ticker,side,quantity,tradePrice,executedAt`

- `trade-import-valid-30d.csv` contains BUY and SELL activity distributed
  across July 2026. BUY rows precede the related SELL rows, so it can be
  imported into a clean database and is useful for exercising the 30D chart.
- `trade-import-valid-decimals.csv` uses the stable ID of the built-in
  `Primary Account` and fractional quantities/prices.
- `trade-import-invalid-errors.csv` intentionally contains an unknown account,
  invalid ticker, invalid side, zero quantity, negative price, and malformed
  timestamp. It should display validation messages and submit no trades.

The two valid files require the built-in `Primary Account` to be ACTIVE. Import
the same valid file a second time to exercise the duplicate-import warning.
