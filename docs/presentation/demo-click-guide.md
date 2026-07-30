# TradeFlow 现场演示：点击步骤与讲稿

演示地址：<http://localhost:3100>

## 演示前确认

1. 打开演示地址。
2. 语言选择 `English`。
3. 确认左下角显示 `Backend Connected`。
4. 确认当前行情模式：
   - 页面显示 `MOCK`：执行默认 Mock 演示。
   - 页面显示 `FINNHUB / LIVE`，并且存在 `Simulate outage`：才执行 Finnhub 故障演示。

**必须遵守**

- 不预设或朗读任何固定的 Finnhub 价格，以页面实际返回值为准。
- `MOCK` 是本地确定性演示数据，不能称为实时行情。
- 没有先成功取得 `FINNHUB / LIVE` 报价时，不演示 `CACHED / STALE`。
- 页面没有 `Simulate outage` 按钮时，直接跳过 Finnhub 故障部分。

**开场英文讲稿**

Hello, we are Group 5. In this demo, we will follow one AAPL trade through booking, position replay, audit-preserving deletion, and portfolio valuation. We will also show that every quote clearly exposes its real source and freshness.

**中文参考**

大家好，我们是第五组。本次演示会用一笔 AAPL 交易，展示交易登记、持仓重算、保留审计记录的删除和组合估值。我们还会展示系统如何如实说明每个行情的来源和新鲜度。

## 1. 建立 Dashboard 基线

**点击步骤**

1. 点击左侧 `Dashboard`。
2. 在右上角 `Account` 中选择 `Demo Growth`。
3. 点击 `Refresh`。
4. 指出：
   - AAPL Quantity = `8`
   - MSFT Quantity = `3`
   - 页面同时存在 Gain 和 Loss
   - 行情标签显示当前真实模式，例如 `MOCK` 或 `FINNHUB`
5. 点击左侧 `Accounts`。
6. 找到 `Demo Growth`，点击 `View positions`。
7. 再次确认 AAPL = `8`、MSFT = `3`。

**英文讲稿**

We begin on the Dashboard with the Demo Growth account. After refresh, AAPL has a quantity of eight and MSFT has a quantity of three. The portfolio includes both a gain and a loss, which makes the valuation result easy to verify. The quote labels are explicit. If the page says MOCK, these are generated demonstration prices and we do not present them as live market data. We can open Demo Growth positions and confirm the same account-level quantities. Please remember that the AAPL baseline is eight.

**中文参考**

我们先查看 Demo Growth 的 Dashboard。刷新后，AAPL 数量为 8，MSFT 数量为 3，同时包含盈利和亏损，便于验证估值结果。行情标签会明确显示来源。如果页面显示 MOCK，就代表这是生成的演示价格，不能称为实时行情。打开 Demo Growth 的持仓，可以确认相同的账户级数量。请记住 AAPL 的基线是 8。

## 2. 登记一笔 AAPL BUY

**点击步骤**

1. 点击左侧 `Trade`。
2. `Account` 选择 `Demo Growth`。
3. `Side` 选择 `BUY`。
4. 在 `Ticker or company` 中输入 `AAPL`。
5. 点击搜索结果中的 AAPL。
6. 确认出现 `Verified: AAPL`。
7. `Quantity` 输入 `1`。
8. `Trade price (USD)` 输入 `100`。
9. 保持当前 `Executed at` 时间。
10. 点击 `Book BUY trade`。
11. 确认成功提示和账本顶部新增的 `BOOKED` AAPL BUY。

**英文讲稿**

Next, we open Trade and select Demo Growth and BUY. We type AAPL and select the search result. The form must display Verified: AAPL, so the ticker is not accepted as an arbitrary string. We enter a quantity of one and a trade price of one hundred dollars, keep the current execution time, and book the trade. The success message appears and a new BOOKED row is added to the ledger. The trade is now an auditable business fact.

**中文参考**

接下来进入 Trade，选择 Demo Growth 和 BUY。输入 AAPL 后点击搜索结果，界面必须出现 Verified: AAPL，因此股票代码不是任意字符串。数量输入 1，交易价格输入 100 美元，保留当前成交时间并提交。成功提示出现，账本顶部新增一条 BOOKED 记录，这笔交易现在成为可审计的业务事实。

## 3. 演示持仓重算与审计保留

**点击步骤**

1. 点击左侧 `Accounts`。
2. 找到 `Demo Growth`，点击 `View positions`。
3. 确认 AAPL 从 `8` 变成 `9`。
4. 点击左侧 `Trade`。
5. 找到刚刚新增的 AAPL BUY。
6. 点击该记录的 `Delete`。
7. 在浏览器确认框中点击 `OK`。
8. 确认原记录仍然存在，并显示：
   - `CANCELLED`
   - `DELETED`
9. 点击左侧 `Accounts`。
10. 再次点击 `Demo Growth` 的 `View positions`。
11. 确认 AAPL 从 `9` 恢复为 `8`。
12. 点击左侧 `Dashboard`。
13. 选择 `Demo Growth`，点击 `Refresh`。

**英文讲稿**

When we view Demo Growth positions again, AAPL moves from eight to nine because the new BOOKED trade is included in chronological replay. We now return to Trade and delete the row we just created. This is not a physical deletion. The original row remains visible as CANCELLED with the reason DELETED. When we view positions again, AAPL returns from nine to eight. The effective position changes, but the evidence remains. Refreshing the Dashboard recalculates valuation from the restored quantity.

**中文参考**

再次查看 Demo Growth 持仓时，AAPL 从 8 变为 9，因为新的 BOOKED 交易已经参与时间顺序重放。回到 Trade 删除刚才的记录，这不是物理删除；原记录仍然保留，并显示 CANCELLED 和 DELETED。再次查看持仓，AAPL 从 9 恢复为 8。有效持仓改变了，但证据仍然存在。刷新 Dashboard 后，估值也会根据恢复后的数量重新计算。

## 4A. 默认 Mock 行情演示

页面显示 `MOCK` 时只执行本节。

**点击步骤**

1. 点击左侧 `Market Data`。
2. `Account` 选择 `Demo Growth`。
3. 找到 AAPL，点击 `Refresh AAPL`。
4. 指出页面的 `MOCK` 和缓存状态标签。
5. 指出页面说明：该价格由本地生成，不是真实市场行情。
6. 不点击 Finnhub 故障按钮。

**英文讲稿**

The default demonstration uses the deterministic Mock provider, so it does not depend on an external API key or venue network. The page explicitly says MOCK and explains that the quote is generated locally, not live market data. We still show the cache state and use the quote for the same backend valuation flow, but we never describe this value as a real market price.

**中文参考**

默认演示使用确定性的 Mock Provider，因此不依赖外部 API Key 或现场网络。页面明确显示 MOCK，并说明这是本地生成的价格，不是真实行情。系统仍然显示缓存状态，并使用相同的后端估值链路，但我们绝不会把这个数值称为真实市场价格。

## 4B. 可选 Finnhub 故障演示

只有页面已经显示 `FINNHUB / LIVE` 且存在 `Simulate outage` 时才执行。

**点击步骤**

1. 点击左侧 `Market Data`。
2. `Account` 选择 `Demo Growth`。
3. 点击 `Refresh AAPL`。
4. 以页面实际结果为准，确认：
   - Source = `FINNHUB`
   - Status = `LIVE`
   - 不朗读预设价格
5. 点击 `Simulate outage`。
6. 再次点击 `Refresh AAPL`。
7. 确认同一笔先前成功取得的报价现在显示：
   - `CACHED`
   - `STALE`
   - 不再显示 `LIVE`
8. 点击左侧 `Dashboard`。
9. 选择 `Demo Growth`，点击 `Refresh`。
10. 指出 Dashboard 的 stale quote 警告。
11. 返回 `Market Data`。
12. 点击 `Restore provider`。
13. 再次点击 `Refresh AAPL`。
14. 只有页面实际恢复成功时，才说明状态恢复为 `LIVE`。

**英文讲稿**

This optional path runs only when the application is genuinely configured for Finnhub and the page has already returned a FINNHUB, LIVE quote. We do not assume or hard-code its price; we use the value actually returned by the provider. After that successful quote has been cached, we simulate a provider outage and refresh AAPL again. The system reuses the previously received Redis value and labels it CACHED and STALE. It does not generate a new price, switch silently to Mock, or claim the value is live. If no retained quote were available, the API would return a clear unavailable response instead of using zero. We then restore the provider and only describe the quote as LIVE if the page actually confirms recovery.

**中文参考**

这个可选流程只有在应用真实配置 Finnhub，并且页面已经成功返回 FINNHUB 和 LIVE 时才能执行。我们不假设或写死价格，只使用 Provider 实际返回的数值。成功报价进入缓存后，模拟 Provider 故障并再次刷新 AAPL。系统复用 Redis 中先前收到的报价，并明确标记为 CACHED 和 STALE。它不会生成新价格、偷偷切换到 Mock，或者把缓存值称为实时行情。如果没有保留报价，API 会明确返回不可用，而不是使用零价格。最后恢复 Provider；只有页面实际确认成功后，才能说明状态恢复为 LIVE。

## 结束讲稿

**英文讲稿**

In one business loop, we booked a verified trade, replayed the position, preserved the audit trail, and valued the portfolio with an explicit quote status. Trades are facts, positions are replayable, and every valuation remains explainable. Thank you.

**中文参考**

通过一条完整业务链路，我们登记了经过验证的交易、重算了持仓、保留了审计记录，并使用状态明确的行情完成组合估值。交易是事实，持仓可以重算，每个估值结果都可以解释。谢谢大家。
