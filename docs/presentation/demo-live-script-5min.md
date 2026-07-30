# Equity Trade Booking Engine：5 分钟 Demo 脚本

推荐地址：`http://localhost:3100`
备用地址：`http://localhost:3000`

## 演示前

1. 语言选择 `English`。
2. 确认 `Backend Connected`。
3. 确认 Market Data 显示 `FINNHUB`，并存在 `Simulate outage`。
4. 确认故障状态为 `OFF`。
5. 准备一只本轮没有查询过的 ticker，例如 `ORCL`。
6. 不朗读固定价格，以页面实际结果为准。

## 0:00–0:30 开场

### 操作

打开 `Dashboard`，选择 `Demo Growth`。

### 中文讲稿

大家好。本次演示会通过一条完整链路，展示交易登记、持仓重算、审计保留和行情故障降级。系统的重点不仅是正常时给出估值，更是在外部数据失败时明确告诉用户哪些数据仍然可信。

### English

This demo follows one complete flow through trade booking, position replay, audit preservation, and market-data fallback. The system not only calculates valuations when everything works, but also makes their trust level explicit when an external provider fails.

## 0:30–1:15 Dashboard 基线

### 操作

1. 点击 `Refresh`。
2. 指出：
   - AAPL Quantity = `8`
   - MSFT Quantity = `3`
   - Total Market Value
   - Unrealized P&L

### 中文讲稿

Demo Growth 当前持有 8 股 AAPL 和 3 股 MSFT。数量和成本来自有效交易流水的时间顺序重算；市场价格只参与估值，不会改变交易事实。市场价值和未实现盈亏统一由后端计算。

### English

Demo Growth currently holds eight AAPL shares and three MSFT shares. Quantity and cost come from chronological replay of effective trades. Quotes are used only for valuation, while market value and unrealized P&L are calculated consistently by the backend.

## 1:15–2:15 交易与审计

### 操作

1. 进入 `Trade`。
2. 选择：
   - Account：`Demo Growth`
   - Side：`BUY`
3. 搜索并选择 `AAPL`。
4. 输入：
   - Quantity：`1`
   - Trade price：`100`
5. 点击 `Book BUY trade`。
6. 进入 `Accounts`，查看 `Demo Growth` 持仓，指出 AAPL 从 `8` 变成 `9`。
7. 返回 `Trade`，删除刚创建的记录并确认。
8. 指出原记录仍显示 `CANCELLED / DELETED`。

### 中文讲稿

我们先选择经过验证的 AAPL，并登记一笔 BUY。交易参与重放后，AAPL 从 8 变成 9。现在删除这笔活动。系统不会物理删除原记录，而是保留它并标记为 CANCELLED 和 DELETED。有效持仓会恢复，但审计证据不会消失。

### English

We select the verified AAPL instrument and book a BUY. After replay, the position moves from eight to nine. We then delete the new activity. The system does not physically remove it; the original row remains as CANCELLED and DELETED. The effective position changes while the audit evidence remains.

## 2:15–3:15 正常行情与 Redis 降级

### 操作

1. 进入 `Market Data`。
2. Account 选择 `Demo Growth`。
3. 点击 `Refresh AAPL`，指出 `FINNHUB + LIVE`。
4. 点击 `Simulate outage`。
5. 指出 AAPL 自动变为：
   - `CACHED`
   - `STALE`
   - 不再显示 `LIVE`

### 中文讲稿

正常刷新直接来自 Finnhub，因此显示 LIVE。现在模拟 Provider 故障。系统获取最新报价失败，于是使用 Redis 中最后一次成功报价。CACHED 表示数据来自 Redis，STALE 表示无法确认它仍然是最新价格。系统不会生成新价格，也不会偷偷切换到 Mock。

### English

The normal refresh comes directly from Finnhub and is marked LIVE. We now simulate a provider outage. The attempt to obtain a new quote fails, so the system uses the last successful value retained in Redis. CACHED identifies the source, while STALE indicates that the value can no longer be confirmed as current.

## 3:15–4:00 无缓存保护

### 操作

1. 保持故障为 `SIMULATED`。
2. 搜索本轮未查询过的 ticker，例如 `ORCL`。
3. 指出：
   - ORCL 显示 `Unavailable`
   - AAPL、MSFT 和其他表格仍然存在

### 中文讲稿

ORCL 没有历史缓存，因此系统明确显示 Unavailable，而不是使用零价格。只有这一只股票受影响，原有持仓和其他搜索结果仍然保留。这说明故障被隔离在具体 ticker，而不是破坏整个页面。

### English

ORCL has no retained quote, so it is explicitly marked Unavailable instead of being assigned a zero price. Only this ticker is affected; the existing positions and searched quotes remain visible.

## 4:00–4:35 Dashboard 降级估值

### 操作

1. 返回 `Dashboard`。
2. 选择 `Demo Growth`。
3. 点击 `Refresh`。
4. 指出 stale warning。

### 中文讲稿

行情故障不会破坏交易和持仓。Dashboard 仍然可以使用最后报价提供有限估值，同时明确警告数据已经过期。交易事实和成本仍然准确，受影响的是估值的新鲜度。

### English

The quote failure does not damage trades or positions. The Dashboard can still provide a limited fallback valuation while clearly warning that the quotes are stale. Trade facts and cost basis remain correct; only valuation freshness is affected.

## 4:35–5:00 恢复与总结

### 操作

1. 返回 `Market Data`。
2. 点击 `Restore provider`。
3. 页面实际恢复 `LIVE` 后结束。

### 中文讲稿

恢复 Finnhub 后，系统重新获取报价并更新 Redis。通过这条链路，我们展示了可审计交易、可重算持仓和可解释的故障降级。系统不会承诺外部 API 永远可用，但会保证异常时不编造数据，并清楚说明当前数据还能相信到什么程度。

### English

After restoring Finnhub, the system fetches new quotes and updates Redis. This flow demonstrates auditable trades, replayable positions, and explainable fallback behavior. The system does not promise that an external API will never fail; it ensures that failures never result in fabricated data and that the trust level of each value remains clear.

## 超时时可删减

如果现场只剩 3–4 分钟：

- 删除 `Accounts` 页面持仓确认，只口述 AAPL 从 8 变成 9。
- 不进入 Dashboard 展示 stale warning。
- 保留 `LIVE → CACHED + STALE → Unavailable → Restore`，这是最重要的技术亮点。
