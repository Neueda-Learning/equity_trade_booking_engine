# Equity Trade Booking Engine：功能清单与现场 Demo 脚本

建议时长：8–10 分钟
推荐演示地址：`http://localhost:3100`（隔离 Demo）
当前开发地址：`http://localhost:3000`

## 一、Demo 可以展示的功能

### 1. 账户与交易

- 创建、编辑和停用多个证券账户；账户不会被物理删除。
- 通过股票代码或公司名称搜索并选择经过验证的美国股票、ADR 或 ETF。
- 登记 BUY、SELL 交易，记录数量、成交价格和实际成交时间。
- 取消、修改和审计保留式删除交易。
- 删除后的原记录继续存在，并显示 `CANCELLED / DELETED`。
- CSV 批量导入交易，并对重复表格给出二次确认。
- 按时间顺序校验持仓，阻止产生负持仓的卖出。

### 2. 持仓与估值

- 根据有效交易流水重算持仓，而不是直接保存一个不可解释的持仓数字。
- 使用加权平均成本计算数量、平均成本和成本基础。
- 统一由后端计算市场价值、未实现盈亏和收益率。
- 支持所有账户汇总或按单个账户查看。
- 缺少报价时保持 `null / UNAVAILABLE`，不会把零当成市场价格。

### 3. Market Data 与 Redis 降级

- 支持明确标记的确定性 `MOCK` 行情和显式配置的 Finnhub 行情。
- 显示报价来源、报价时间、本次获取时间和涨跌数据。
- 正常直取 Finnhub 时显示 `FINNHUB + LIVE`。
- Redis 新鲜缓存命中时显示 `CACHED`。
- Finnhub 故障且 Redis 有历史报价时显示 `CACHED + STALE`。
- Finnhub 故障且没有 Redis 报价时显示 `UNAVAILABLE`，不会切换成 Mock。
- 提供 Demo-only 的 `Simulate outage / Restore provider` 控制。
- 搜索过的 ticker 会累计显示在 `Searched quotes` 中，而不是互相覆盖。
- 搜索 ticker 列表保存在浏览器本地；报价本身仍然每次从后端获取。
- 单只搜索股票不可用时，只影响该行，持仓报价和其他搜索结果继续显示。

### 4. Dashboard 与历史

- 展示总市场价值、总成本、未实现盈亏、收益率和持仓数量。
- 每个持仓显示价格、估值、盈亏和行情状态。
- Redis 降级时显示组合级 stale 警告。
- 保存本地估值快照，并支持 `1D / 7D / 30D / ALL` 时间范围。
- 展示最近交易活动和持仓分配。

### 5. 工程与交付能力

- Spring Boot、React、MySQL、Redis 组成模块化单体。
- MySQL 是账户、交易和估值历史的系统记录；Redis 只是可丢弃行情缓存。
- 提供健康检查、Problem Details、OpenAPI 和 Swagger UI。
- 支持 English、中文和 Português。
- 提供桌面和移动端响应式界面。

## 二、演示前准备

### 1. 启动隔离 Finnhub Demo

不要把真实密钥写入仓库、截图或讲稿。

```bash
export MARKET_DATA_PROVIDER=finnhub
export FINNHUB_API_KEY='<local secret>'
bash scripts/demo-up.sh
bash scripts/demo-seed.sh
```

打开：

- Demo UI：`http://localhost:3100`
- Swagger：`http://localhost:8180/swagger-ui.html`

如果使用当前开发栈，则打开 `http://localhost:3000`。

### 2. 现场检查

1. 语言选择 `English`。
2. 确认左下角显示 `Backend Connected`。
3. 进入 `Market Data`，确认：
   - 来源显示 `FINNHUB`。
   - 页面存在 `Simulate outage`。
   - `Provider outage` 为 `OFF`；如果不是，点击 `Restore provider`。
4. 点击一次 `Refresh AAPL`，确保现场网络和 Finnhub 可用。
5. 为无缓存演示准备一只本轮尚未查询过的真实 ticker，例如：
   - `ORCL`
   - `IBM`
   - `INTC`
   - `ADBE`
   - `CSCO`
6. 不背诵或预设任何 Finnhub 价格，以页面实际值为准。

说明：Redis 报价默认保留 24 小时。多次排练时应轮换“未查询 ticker”，否则它可能已经有缓存，无法展示 `UNAVAILABLE`。

## 三、8–10 分钟主 Demo

## 0. 开场（20–30 秒）

### 操作

保持在 `Dashboard`。

### 中文讲稿

大家好，我们是第五组。本次演示会沿着一条完整业务链路，展示账户、交易登记、持仓重算、审计保留、组合估值，以及外部行情故障时的 Redis 降级。我们的重点不只是正常情况下显示数字，也包括异常情况下系统是否诚实地说明这些数字还能相信到什么程度。

### English

Hello, we are Group 5. This demo follows one complete business flow through account management, trade booking, position replay, audit preservation, portfolio valuation, and Redis fallback during a market-data outage. Our focus is not only producing numbers when everything works, but also explaining what remains trustworthy when an external dependency fails.

## 1. Dashboard 基线（约 1 分钟）

### 操作

1. 点击 `Dashboard`。
2. `Account` 选择 `Demo Growth`。
3. 点击 `Refresh`。
4. 指出：
   - AAPL Quantity = `8`
   - MSFT Quantity = `3`
   - Total Market Value、Total Cost Basis 和 Unrealized P&L
   - 每只股票的行情来源与状态
5. 如估值历史已有数据，快速切换 `1D` 或 `ALL`。

### 中文讲稿

我们从 Demo Growth 账户开始。AAPL 的持仓数量是 8，MSFT 是 3。数量和成本来自有效交易流水的时间顺序重算；市场价格只参与估值，不会改变交易事实。总市场价值、总成本和未实现盈亏都由后端使用同一套计算模型生成。

### English

We begin with the Demo Growth account. AAPL has a quantity of eight and MSFT has three. Quantity and cost come from chronological replay of effective trades. Market quotes are used only for valuation and never change the underlying trade facts. The backend calculates market value, cost basis, and unrealized P&L consistently.

## 2. 登记一笔交易（约 1.5 分钟）

### 操作

1. 点击 `Trade`。
2. `Account` 选择 `Demo Growth`。
3. `Side` 选择 `BUY`。
4. 在 `Ticker or company` 输入 `AAPL`。
5. 点击搜索结果中的 AAPL。
6. 确认出现 `Verified: AAPL`。
7. `Quantity` 输入 `1`。
8. `Trade price (USD)` 输入 `100`。
9. 保持当前 `Executed at`。
10. 点击 `Book BUY trade`。
11. 确认成功提示和新的 `BOOKED` 记录。

### 中文讲稿

交易登记不是接收任意字符串。我们先搜索并选择经过验证的 AAPL，然后登记一笔数量为 1、价格为 100 美元的 BUY。提交后，交易进入账本，成为可审计的业务事实。

### English

Trade booking does not accept an arbitrary ticker string. We search for and select the verified AAPL instrument, then book a BUY of one share at one hundred dollars. The new BOOKED row becomes an auditable business fact.

## 3. 持仓重算与审计保留（约 1.5 分钟）

### 操作

1. 点击 `Accounts`。
2. 找到 `Demo Growth`，点击 `View positions`。
3. 确认 AAPL 从 `8` 变成 `9`。
4. 返回 `Trade`。
5. 找到刚才新增的 AAPL BUY，点击 `Delete`。
6. 在确认框点击 `OK`。
7. 确认原记录仍然存在，并显示：
   - `CANCELLED`
   - `DELETED`
8. 返回 `Accounts`，再次查看 `Demo Growth` 持仓。
9. 确认 AAPL 从 `9` 恢复为 `8`。

### 中文讲稿

新增交易参与重放后，AAPL 从 8 变成 9。现在删除这笔活动。这里的删除不是物理删除：原始记录仍然存在，并标记为 CANCELLED 和 DELETED。再次查看持仓时，AAPL 回到 8。有效业务结果发生了变化，但审计证据没有消失。

### English

After replaying the new trade, AAPL moves from eight to nine. We now delete that activity. This is not a physical deletion: the original record remains visible as CANCELLED with the DELETED reason. The effective position returns to eight while the audit evidence remains intact.

## 4. 行情搜索与正常状态（约 1 分钟）

### 操作

1. 点击 `Market Data`。
2. `Account` 选择 `Demo Growth`。
3. 点击 `Refresh AAPL`。
4. 指出 `FINNHUB + LIVE`。
5. 在 `Ticker search` 依次搜索：
   - `AAPL`
   - `MSFT`
6. 指出 `Searched quotes` 同时保留两只股票。

### 中文讲稿

这次刷新直接成功来自 Finnhub，因此状态是 FINNHUB 和 LIVE。这里的 LIVE 表示本次请求直接取自 Provider，不代表交易所当前一定开盘。报价时间表示市场报价发生的时点，更新时间表示系统何时成功取得这份数据。搜索结果会累计保留，但浏览器只保存 ticker 列表，价格仍然从后端重新获取。

### English

This refresh succeeds directly against Finnhub, so the quote is marked FINNHUB and LIVE. LIVE means that this request came directly from the provider; it does not claim that the exchange is currently open. Quote time is the market timestamp, while Updated shows when our system retrieved it. Searched tickers accumulate, but the browser stores only the ticker list—the quote itself is always fetched again from the backend.

## 5. Redis 降级与 STALE（约 1 分钟）

### 操作

1. 点击 `Simulate outage`。
2. 当前实现会自动刷新页面中可见的股票，不需要再次点击 `Refresh AAPL`。
3. 指出 AAPL 或 MSFT：
   - 价格保持为最后一次成功报价
   - 显示 `CACHED`
   - 显示 `STALE`
   - 不再显示 `LIVE`

### 中文讲稿

现在我们模拟 Finnhub 故障。系统尝试获取最新报价失败，于是返回 Redis 中最后一次成功保存的数据。CACHED 表示数据来自 Redis；STALE 表示系统无法确认它仍然是最新价格。价格保持不变正是预期结果，因为系统复用的是同一份历史报价，而不是重新生成数据。

### English

We now simulate a Finnhub outage. The attempt to obtain a new quote fails, so the system returns the last successful value retained in Redis. CACHED identifies the storage source, while STALE says that the value can no longer be confirmed as current. The unchanged price is expected because the system is reusing the same historical quote rather than generating a replacement.

## 6. 无缓存时 UNAVAILABLE（约 1 分钟）

### 操作

1. 保持故障为 `SIMULATED`。
2. 在 `Ticker search` 输入演示前准备的、当前 Redis 中没有缓存的 ticker，例如 `ORCL`。
3. 点击 `Search`。
4. 指出：
   - ORCL 行显示 `UNAVAILABLE`
   - AAPL、MSFT 和 `Position quotes` 仍然保留
   - 页面没有使用零价格，也没有切换到 Mock

### 中文讲稿

接下来搜索一只从未缓存过的股票。由于 Finnhub 当前不可用，Redis 里也没有历史报价，因此系统明确显示 UNAVAILABLE。注意，只有这一只股票不可用；其他持仓和搜索结果仍然保留。系统不会用零价格，也不会偷偷切换到 Mock。

### English

Next, we search for a ticker that has never been cached. Finnhub is unavailable and Redis has no retained value, so this row is explicitly marked UNAVAILABLE. Only this ticker is affected; the existing positions and searched quotes remain visible. The system does not substitute zero or silently switch to Mock data.

## 7. Dashboard 降级估值（约 45 秒）

### 操作

1. 点击 `Dashboard`。
2. `Account` 选择 `Demo Growth`。
3. 点击 `Refresh`。
4. 指出顶部 stale quote 警告和持仓行的 `CACHED / STALE`。

### 中文讲稿

外部行情故障没有破坏交易或持仓。Dashboard 仍然可以使用最后一次成功报价提供有限的降级估值，同时明确警告这些数值不是最新行情。交易事实和成本仍然准确，受影响的是估值的新鲜度。

### English

The external quote failure does not damage trades or positions. The Dashboard can still provide a limited fallback valuation from the last successful quotes, while clearly warning that the values are not current. Trade facts and cost basis remain correct; only valuation freshness is degraded.

## 8. 恢复数据源（约 30 秒）

### 操作

1. 返回 `Market Data`。
2. 指出之前搜索的 ticker 仍在 `Searched quotes`。
3. 点击 `Restore provider`。
4. 页面会自动重新刷新可见 ticker。
5. 只有页面实际成功显示 `LIVE` 时，才说明恢复完成。

### 中文讲稿

搜索列表在页面切换后仍然保留。现在恢复 Finnhub，系统重新获取报价并更新 Redis。只有在页面实际重新显示 LIVE 后，我们才认为 Provider 已恢复。

### English

The searched-ticker list remains available after navigation. We now restore Finnhub, fetch new quotes, and repopulate Redis. We describe the provider as recovered only after the page actually returns to LIVE.

## 9. 收尾（20–30 秒）

### 中文讲稿

通过这条业务链路，我们展示了经过验证的交易登记、可重算持仓、审计保留式删除、一致的组合估值，以及行情故障时的 Redis 降级和无缓存保护。这个系统的价值不是承诺外部 API 永远可用，而是在异常发生时保证交易事实不受影响，并清楚告诉用户当前数据还能相信到什么程度。

### English

In one business flow, we demonstrated verified trade booking, replayable positions, audit-preserving deletion, consistent portfolio valuation, Redis fallback, and explicit no-cache behavior. The system does not promise that an external API will never fail. Its value is preserving business facts and making the trust level of every valuation explicit when failures occur.

## 四、可选加分项

时间充足时再展示，避免主流程超时。

### CSV 批量导入

1. 进入 `Trade`。
2. 点击 `Open importer`。
3. 下载示例或选择准备好的 CSV。
4. 展示逐行校验、导入结果和重复表格确认。

讲解重点：

> CSV 导入不是绕过业务规则的后门。每一行仍然经过账户、ticker、数量、价格、时间和负持仓校验，重复的完整表格会被识别并要求显式确认。

### Swagger / API 合同

打开 `http://localhost:8180/swagger-ui.html`，快速指出：

- Account、Trade、Position、Market Data、Dashboard API。
- Problem Details 错误格式。
- Demo-only outage API。
- `LIVE / CACHED / STALE / UNAVAILABLE` 合同。

## 五、现场风险与备用话术

### 美股闭市

> The provider is available, but the exchange may be closed. LIVE means this request came directly from Finnhub. The quote timestamp may therefore remain at the latest trading session.

### Finnhub 价格没有变化

> Price equality is not our failure signal. The source, cached flag, stale flag, and retrieval timestamp show which path the system used.

### Finnhub 限流或现场网络失败

> The provider is currently unavailable. The system is behaving as designed: retained quotes are marked CACHED and STALE, while uncached tickers are marked UNAVAILABLE.

### “为什么不用零价格？”

> Zero is a valid numeric value but an invalid substitute for a missing market quote. Using zero would corrupt market value and P&L, so missing prices remain unavailable.

### “为什么 CACHED 和 STALE 同时出现？”

> CACHED describes where the value came from—Redis. STALE describes whether it can still be confirmed as current. A Redis fallback during a provider failure is correctly both CACHED and STALE.
