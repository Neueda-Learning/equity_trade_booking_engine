# Equity Trade Booking Engine — Bilingual Q&A Answer Templates

> Based on the current repository implementation as of 30 July 2026.
>
> 建议每题控制在 30–60 秒。第 4、8、15 题包含团队或现场因素，答辩前请用真实姓名和真实经历替换方括号内容。

## Easy — Comprehension and Demo Follow-up

### 1. In one sentence, who is your user and what problem does your app solve for them?

**中文回答模板**

我们的目标用户是需要管理多个美元证券账户的个人投资者或后台操作人员；系统帮助他们以可审计的方式登记股票买卖，并从有效交易重算持仓，再结合来源和新鲜度明确的行情计算未实现盈亏。

**English answer template**

Our target user is an individual investor or back-office operator managing multiple USD securities accounts; the system lets them book equity trades with a complete audit trail, replay positions from valid trades, and calculate unrealized P&L from quotes whose source and freshness are explicit.

---

### 2. What was your minimum viable product on day one, and what did you add in later increments?

**中文回答模板**

第一版 MVP 是一条很薄的端到端链路：Spring Boot 和 React 可以启动，用户能够登记一笔 BUY 交易，交易保存到 MySQL，并通过 API 查询。之后我们按增量加入多账户和 Activity、SELL 与交易生命周期、按时间重放的持仓、Redis Mock 行情、P&L Dashboard、Finnhub 和故障降级、交易修改、精确小数、多语言、CSV 批量导入与重复检测，以及本地行情和估值历史。这样每个阶段都保持可运行，而不是最后一次性集成。

**English answer template**

Our first MVP was a very thin end-to-end slice: Spring Boot and React could start, a user could book one BUY trade, persist it in MySQL, and read it through the API. Later increments added multi-account Activity, SELL and lifecycle handling, chronological position replay, Redis-backed Mock quotes, the P&L Dashboard, Finnhub and resilient fallback, amendment, exact decimal handling, multilingual UI, CSV import with duplicate detection, and locally persisted quote and valuation history. Each increment remained runnable instead of postponing integration until the end.

---

### 3. Which external API did you integrate, and what does your system do with that data?

**中文回答模板**

我们集成的是 Finnhub。系统使用它进行美股、ADR 和 ETF 搜索，并获取当前报价；成功报价会进入 Redis 缓存，同时追加到 MySQL 的行情快照。后端把报价与重算后的 Position 组合，计算市值和未实现盈亏。默认演示可以使用确定性的 Mock Provider，但界面会明确标记 MOCK，绝不会把生成数据展示成真实行情。

**English answer template**

We integrated Finnhub for supported U.S. stock, ADR, and ETF search and for current market quotes. Successful quotes are cached in Redis and appended to MySQL quote snapshots. The backend combines those quotes with replayed positions to calculate market value and unrealized P&L. The default demo can use a deterministic Mock provider, but it is always labelled MOCK and is never presented as real market data.

---

### 4. How did you split roles across backend, frontend, and integration work?

**中文回答模板**

我们围绕可交付的业务切片分工，而不是把成员完全隔离在技术层中。[成员 A] 主要负责后端领域规则、REST API 和 MySQL；[成员 B] 主要负责 React 页面、交互和多语言；[成员 C] 主要负责 Finnhub、Redis、Docker Compose 和 CI；[成员 D] 主要负责测试、演示数据和文档。API 契约、跨层集成和 PR Review 由团队共同完成，因此每个功能都有明确负责人，但关键知识不会只集中在一个人身上。

**English answer template**

We organised ownership around deliverable business slices rather than isolating everyone permanently by technical layer. [Member A] mainly owned backend domain rules, REST APIs, and MySQL; [Member B] owned the React experience and localisation; [Member C] owned Finnhub, Redis, Docker Compose, and CI; and [Member D] focused on testing, demo data, and documentation. API contracts, cross-layer integration, and pull-request reviews were shared, so each feature had a clear owner without concentrating all critical knowledge in one person.

> Replace the names and responsibilities above with the team's actual allocation.

---

### 5. Can you walk us through your main database entities and how they connect?

**中文回答模板**

核心关系是一个 Account 对应多笔 Trade。Trade 保存账户、ticker、BUY/SELL、数量、价格、执行时间和 BOOKED/CANCELLED 状态；修改交易不会覆盖原记录，而是通过 `supersedesTradeId` 关联替代交易。Position 不是独立事实表，而是从 BOOKED Trade 按时间重放得到。MySQL 还保存 CSV Import Registry、Market Quote Snapshot 和 Valuation Snapshot；Redis 只保存可丢弃的当前行情缓存，不是业务事实来源。

**English answer template**

The core relationship is one Account to many Trades. A Trade stores the account, ticker, BUY or SELL side, quantity, price, execution time, and BOOKED or CANCELLED status. An amendment does not overwrite the original; it creates a replacement linked by `supersedesTradeId`. Position is not an independent fact table—it is derived by replaying BOOKED trades chronologically. MySQL also stores the CSV Import Registry, Market Quote Snapshots, and Valuation Snapshots. Redis contains only disposable current-quote cache data and is not the system of record.

## Medium — Decisions, Resilience, and Process

### 6. What did you deliberately leave out of scope, and why?

**中文回答模板**

我们有意把范围限制为单用户、仅 USD、禁止做空、加权平均成本和未实现盈亏。当前没有认证授权、现金账本、多币种、已实现盈亏、FIFO/LIFO 税务批次、保证金、公司行动或订单撮合。原因是本项目首先要证明交易事实、持仓一致性和行情韧性这条闭环；如果同时实现完整券商或会计平台，会扩大风险并削弱核心规则的可验证性。

**English answer template**

We deliberately limited the product to a single user, USD, no short selling, weighted-average cost, and unrealized P&L. It does not yet include authentication, a cash ledger, multiple currencies, realized P&L, FIFO or LIFO tax lots, margin, corporate actions, or order matching. Our priority was to prove the closed loop of trade facts, position integrity, and market-data resilience. Building a complete brokerage or accounting platform at the same time would have increased risk and made the core rules harder to verify.

---

### 7. What happens when your external data source is slow, rate-limited, or unavailable?

**中文回答模板**

Finnhub 调用有连接和读取超时，总尝试次数最多为两次；只对超时、连接失败和 5xx 做一次有限重试，不会盲目重试 4xx、429 或格式错误。成功报价写入 Redis：新鲜期内可以直接复用，超过新鲜期但仍在保留期内，只能在 Provider 故障时作为 stale fallback。返回值和界面明确标记 LIVE、CACHED、STALE 或 MOCK。如果 Provider 失败且没有缓存，API 返回清晰的 503 Problem Details，不会自动伪装成 Mock，也不会用零价格估值。

**English answer template**

Finnhub calls have connection and read timeouts with at most two total attempts. We retry once only for timeouts, connection failures, and 5xx responses; we do not blindly retry 4xx, 429, or malformed data. Successful quotes go into Redis. A quote can be reused normally during the fresh window, while a retained quote is available only as a stale fallback during provider failure. The API and UI explicitly label LIVE, CACHED, STALE, or MOCK states. If the provider fails and no cache exists, the API returns clear 503 Problem Details—it never silently switches to Mock or values the position at zero.

---

### 8. What was the hardest bug or integration issue you hit, and how did you resolve it?

**中文回答模板**

一个很有代表性的问题是金融小数精度。后端使用 `BigDecimal`，但最初通过 JSON 数字传给浏览器后会进入 JavaScript `number`，可能产生 IEEE-754 精度损失，导致交易、持仓和 P&L 在边界值上显示不一致。我们把所有金融小数统一编码为 JSON 字符串，在 TypeScript 中引入 `Decimal = string`，并使用字符串安全的格式化和计算边界。最后补充了 OpenAPI、API 集成和前端回归测试，确保精度不会再次被某一层悄悄破坏。

**English answer template**

A representative hard issue was financial decimal precision. The backend used `BigDecimal`, but when values were originally sent as JSON numbers they became JavaScript `number` values and could lose precision through IEEE-754. That could make trade, position, and P&L values disagree at edge cases. We changed all financial decimals to exact JSON strings, introduced `Decimal = string` in TypeScript, and used string-safe formatting and calculation boundaries. We then added OpenAPI, API integration, and frontend regression tests so no layer can silently reintroduce the precision loss.

> If the team prefers a more personal example, replace this with the real issue owned by the speaker and retain the structure: symptom → root cause → fix → regression protection.

---

### 9. How did you use branches, pull requests, and tags in git — and what would you improve?

**中文回答模板**

我们按功能使用 Feature Branch，例如账户与 Activity、交易生命周期、Redis 行情、P&L Dashboard、实时行情韧性、CSV Import 和多语言，然后通过 Pull Request 合并到 `main`。PR 和 push 都触发后端、前端、Compose smoke、Playwright E2E 和统一 quality gate；仓库也使用了 `v1.0` 标签记录阶段性版本。可以改进的地方是让 PR 更小、更频繁，统一 Conventional Commit 文案，强制 branch protection 和 review，并为每个版本标签补充清晰的 release notes。

**English answer template**

We used feature branches for capabilities such as Accounts and Activity, trade lifecycle, Redis market data, the P&L Dashboard, live-data resilience, CSV import, and localisation, and merged them through pull requests into `main`. Pull requests and pushes run backend, frontend, Compose smoke, Playwright E2E, and the final quality gate. We also used the `v1.0` tag for a release milestone. We would improve the workflow by keeping pull requests smaller and more frequent, using consistent Conventional Commit messages, enforcing branch protection and reviews, and attaching clear release notes to every version tag.

---

### 10. If we skipped your UI and hit your REST API directly right now, which endpoint best proves the system works?

**中文回答模板**

最能证明完整闭环的是 `POST /api/dashboard/refresh`，也可以带 `accountId`。它会读取当前 BOOKED Trades，重算 Position，为每个持仓 ticker 强制刷新行情，保留部分成功结果，计算未实现盈亏，并把 ALL 或 ACCOUNT 估值快照写入 MySQL。响应同时包含 totals、逐持仓 P&L、近期 Activity、quote status 和 `capturedAt`，所以一个 endpoint 就能展示交易、持仓、行情、估值和持久化的集成结果。所有契约也可以在 Swagger 中直接验证。

```bash
curl -X POST 'http://localhost:8080/api/dashboard/refresh'
```

**English answer template**

The strongest cross-cutting endpoint is `POST /api/dashboard/refresh`, optionally with an `accountId`. It reads current BOOKED trades, recalculates positions, force-refreshes each position ticker, retains partial quote successes, calculates unrealized P&L, and writes an ALL or ACCOUNT valuation snapshot to MySQL. Its response contains totals, position-level P&L, recent Activity, quote status, and `capturedAt`, so one endpoint demonstrates the integration of trades, positions, market data, valuation, and persistence. The complete contract is also directly testable in Swagger.

```bash
curl -X POST 'http://localhost:8080/api/dashboard/refresh'
```

## Hard — Architecture, Risk, and Judgment

### 11. How reliable is the suggested live demo moment, and what is your backup plan if it fails?

**中文回答模板**

演示环境使用独立的 `equity-demo` Compose Project、独立端口和 Volume，`demo-seed.sh` 是幂等的，因此可以安全重复准备相同的账户、交易和盈亏基线。默认 Mock 模式不需要外部 Key，适合保证核心业务演示稳定。Finnhub 演示时，我们会先成功刷新并预热 Redis，再启用 Demo Outage；预期结果是报价从 LIVE 变成 CACHED 和 STALE。该路径已经由 Compose smoke 和桌面、移动端 Playwright 测试覆盖。若真实 Finnhub 或网络现场失败，我们不会伪装成功，而是切换到确定性的 Mock 或本地 Finnhub-compatible Stub，并使用预先保存的 API 响应或截图说明 stale fallback。

**English answer template**

The demo uses an isolated `equity-demo` Compose project with separate ports and volumes, and `demo-seed.sh` is idempotent, so we can recreate the same accounts, trades, and P&L baseline safely. The default Mock mode requires no external key and keeps the core business demo reliable. For the Finnhub outage moment, we first refresh successfully to warm Redis and then enable the Demo Outage; the expected transition is from LIVE to CACHED and STALE. Compose smoke and Playwright desktop and mobile tests cover this path. If the real Finnhub service or venue network fails, we do not pretend it worked—we switch to deterministic Mock or the local Finnhub-compatible stub and use a prepared API response or screenshot to explain the stale-fallback behavior.

---

### 12. Where does your system stop short of making a financial or compliance decision automatically?

**中文回答模板**

系统只登记交易事实、执行一致性校验并计算估值，不会自动建议、批准或执行 BUY/SELL，也不会把 P&L 当成投资建议。Mock、Live、Cached、Stale 和 incomplete 状态都会明确展示；如果行情不可用，值保持为 `null`，而不是编造价格。禁止负持仓是数据完整性规则，不是投资决策。真正的交易审批、适当性、风控、合规判断和订单执行仍然需要外部系统或人工负责。

**English answer template**

The system records trade facts, enforces consistency rules, and calculates valuation; it does not recommend, approve, or execute BUY or SELL decisions, and it does not present P&L as investment advice. MOCK, LIVE, CACHED, STALE, and incomplete states are explicit. If a quote is unavailable, the value remains `null` rather than being invented. Preventing a negative position is a data-integrity rule, not an investment decision. Trade approval, suitability, risk, compliance determinations, and execution remain the responsibility of people or external systems.

---

### 13. What data-integrity or concurrency risks exist, and how did you guard against them?

**中文回答模板**

我们重点处理了四类风险。第一，并发 SELL、取消或修改可能造成负持仓；相关操作在事务中锁定 Account 行，并按执行时间、操作时间和 UUID 重放交易，任何时间点为负都会返回 409。第二，取消操作幂等，删除是带原因的软取消，修改在一个事务中取消原交易并创建审计关联的 replacement。第三，CSV 重复导入使用规范化表内容的 SHA-256 指纹、唯一约束和 `for update` 锁；只有用户明确确认才能再次导入完整表。第四，陈旧或缺失行情不会伪装成当前值，系统暴露 stale/incomplete 状态，缺失价格保持 `null`。

**English answer template**

We focused on four risks. First, concurrent SELL, cancellation, or amendment could create a negative position. Those operations run transactionally, lock the Account row, and replay trades by execution time, operation time, and UUID; any negative point returns a 409 conflict. Second, cancellation is idempotent, deletion is a reasoned soft cancellation, and amendment cancels the original and creates an audit-linked replacement in one transaction. Third, CSV duplicate detection uses a SHA-256 fingerprint of normalised table content, a uniqueness constraint, and `for update` locking; importing the complete table again requires explicit confirmation. Fourth, stale or missing quotes are never presented as current values—the system exposes stale or incomplete status, and missing prices remain `null`.

---

### 14. If this went to a small pilot with real users in four weeks, what are the top three gaps you would close first?

**中文回答模板**

第一优先级是身份、授权和数据归属，包括登录、角色、账户级访问控制和带操作者身份的审计。第二是生产安全与运行能力，包括 Secret Manager、TLS、Key 轮换、集中日志、指标告警、数据库备份和恢复演练。第三是金融数据生产化，包括有授权和 SLA 的行情源、rate-limit 监控与对账，以及根据 Pilot 需求补充现金账本和已实现盈亏。四周内我们不会追求更多页面，而会先确保真实用户的数据隔离、可恢复和可解释。

**English answer template**

My first priority would be identity, authorisation, and data ownership: login, roles, account-level access control, and audit records tied to an operator. Second would be production security and operations: a secret manager, TLS, key rotation, centralised logs, metrics and alerts, database backups, and recovery exercises. Third would be production-grade financial data: a licensed provider with an SLA, rate-limit monitoring and reconciliation, plus a cash ledger and realised P&L if the pilot requires them. In four weeks, we would prioritise isolation, recoverability, and explainability over adding more screens.

---

### 15. What architectural choice did you make differently from another team, and would you keep it?

**中文回答模板**

与采用 `[微服务 / 独立行情 Worker / 前端直接调用外部 API]` 的 `[第 X 组]` 相比，我们选择了 Spring Boot 模块化单体，并通过领域 Port 把 MySQL、Redis、Finnhub 和调度器放在 Infrastructure 层。这个选择减少了分布式事务、部署和调试成本，同时仍然用 Account、Trade、Position、Market Data 和 P&L 模块保持边界。再多一周我仍会保留模块化单体，但会进一步强化模块契约和可观测性；只有当行情采集需要独立扩缩容或故障隔离时，才考虑把行情采集和快照调度抽成独立服务。

**English answer template**

Compared with [Team X], which used `[microservices / a separate market-data worker / direct external API calls from the frontend]`, we chose a Spring Boot modular monolith and placed MySQL, Redis, Finnhub, and scheduling adapters behind domain ports in the infrastructure layer. This reduced distributed-transaction, deployment, and debugging cost while preserving clear Account, Trade, Position, Market Data, and P&L module boundaries. With another week, I would keep the modular monolith and strengthen module contracts and observability. I would extract quote ingestion and snapshot scheduling only when they genuinely need independent scaling or failure isolation.

> This answer must reference an architectural choice actually observed in another team's presentation. Replace both bracketed sections before the Q&A.

## Quick Closing Line

**中文**

我们的核心设计原则是：交易是可审计的事实，持仓必须能够重算，行情状态必须透明，所有估值都必须可以解释和验证。

**English**

Our core design principle is that trades are auditable facts, positions must be replayable, quote status must be transparent, and every valuation must be explainable and verifiable.
