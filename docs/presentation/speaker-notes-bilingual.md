# Equity Trade Booking Engine — Bilingual Speaker Notes

建议时长：10–12 分钟。每页先给出中文稿，再给出等义英文稿；可任选一种语言完整演讲，也可按听众情况切换。

## Slide 1 — 股票交易记账引擎 / Equity Trade Booking Engine

### 中文

大家好，我们是第五组 Give me five。今天介绍的项目是股票交易记账引擎。它面向单用户、多证券账户场景，把账户管理、买卖记账、交易生命周期、持仓计算、市场行情和未实现盈亏放在同一条可审计的业务链路中。我们希望展示的不只是一个页面应用，而是一套在数据一致性、故障降级和工程质量上都可以解释、验证和演示的交易后台。

### English

Hello everyone. We are Group 5, “Give me five.” Today we are presenting the Equity Trade Booking Engine. It is designed for a single user managing multiple securities accounts, and it connects account setup, trade booking, lifecycle management, position calculation, market quotes, and unrealized P&L in one auditable workflow. Our goal is not simply to demonstrate a user interface. We want to show a trading back office whose data integrity, failure behavior, and engineering quality can all be explained, tested, and demonstrated.

## Slide 2 — 从碎片操作到可信闭环 / From Fragmented Tasks to a Trusted Loop

### 中文

传统的演示项目常把账户、交易、行情和盈亏拆成互不关联的 CRUD 页面，这会留下三个问题：交易修改后是否还能追溯，卖出或撤单后持仓是否仍然正确，行情服务失败时系统会不会悄悄显示错误数据。本项目把这些问题统一为一个闭环：交易是事实，持仓由有效交易重放得到，行情只负责估值，盈亏由后端统一计算。这样每个数字都能追溯到来源，每种异常也有明确语义。

### English

Many demonstration systems treat accounts, trades, quotes, and P&L as unrelated CRUD screens. That leaves three important questions unanswered: can an amended trade still be audited, does a sell or cancellation keep the position correct, and what happens when the quote provider fails? This project turns those concerns into one closed loop. Trades are the business facts, positions are replayed from valid trades, market data is used only for valuation, and P&L is calculated centrally by the backend. As a result, every number has a traceable origin and every failure state has an explicit meaning.

## Slide 3 — 端到端业务闭环 / End-to-End Business Loop

### 中文

业务从创建证券账户开始。用户选择经过校验的美股、ADR 或 ETF，录入买卖方向、数量、价格和执行时间。系统按照时间顺序重放已入账交易，得到按账户和股票代码聚合的持仓。随后获取行情并计算市值、成本和未实现盈亏，最后把最新估值和历史快照呈现在 Dashboard。取消、删除或修改交易都会重新影响下游持仓和盈亏，但不会抹掉原始审计记录。

### English

The workflow starts with a securities account. The user selects a verified U.S. stock, ADR, or ETF and records the side, quantity, trade price, and execution time. The system replays booked trades chronologically to derive positions by account and ticker. It then obtains quotes, calculates market value, cost basis, and unrealized P&L, and presents both the current valuation and persistent history on the dashboard. Cancelling, deleting, or amending a trade changes downstream positions and P&L, while the original audit evidence remains intact.

## Slide 4 — 交易生命周期与业务不变量 / Lifecycle and Business Invariants

### 中文

交易生命周期的核心不是“能不能改”，而是“改完之后还能不能说明发生过什么”。正常交易状态是 BOOKED；取消后变为 CANCELLED，并记录取消原因。删除是保留审计的软删除，修改则取消原交易并创建一条 replacement，通过 supersedesTradeId 建立关联。系统还有三条不变量：取消操作幂等；任何时间点都不允许形成负持仓；持仓只由 BOOKED 交易参与计算。这些规则同时存在于领域模型和测试中。

### English

The key lifecycle question is not whether a trade can be changed, but whether we can still explain what happened afterward. A normal trade is BOOKED. Cancellation moves it to CANCELLED and records a reason. Deletion is audit-preserving soft cancellation, while amendment cancels the original and creates a replacement linked through `supersedesTradeId`. Three invariants protect the model: cancellation is idempotent, the chronological trade timeline may never produce a negative position, and only BOOKED trades contribute to positions. These rules are represented in both the domain model and the automated tests.

## Slide 5 — 持仓与盈亏计算 / Position and P&L Calculation

### 中文

持仓采用加权平均成本。买入会增加数量和成本基础，卖出按当前平均成本减少成本基础，因此不会因为卖出价格改变剩余持仓的平均成本。估值公式很直接：市值等于数量乘市场价，未实现盈亏等于市值减成本基础，收益率等于未实现盈亏除以成本基础。所有核心计算都在后端使用十进制数完成，避免前端浮点误差。若行情缺失，值保持为 null，界面不会把零价格伪装成真实行情。

### English

Positions use weighted-average cost. A buy increases quantity and cost basis. A sell reduces cost basis at the current average cost, so the sale price does not distort the average cost of the remaining position. Valuation is straightforward: market value equals quantity times market price; unrealized P&L equals market value minus cost basis; and return percentage equals unrealized P&L divided by cost basis. Core calculations use backend decimal arithmetic to avoid browser floating-point differences. If a quote is missing, the value stays null—the UI never invents a zero market price.

## Slide 6 — 模块化单体架构 / Modular Monolith Architecture

### 中文

系统采用模块化单体，而不是为了形式拆分微服务。前端由 React 和 TypeScript 构建，通过 Nginx 反向代理访问 Spring Boot API。后端按 Account、Trade、Position、Market Data 和 P&L 划分业务模块，每个模块遵循 API 到 Application 到 Domain 的依赖方向，Infrastructure 通过端口适配领域。MySQL 保存账户、交易和估值快照，是系统事实来源；Redis 只缓存行情，可以随时丢弃，不承载唯一业务数据。这种边界降低了部署复杂度，也保留了未来拆分的可能。

### English

The system uses a modular monolith rather than splitting into microservices for appearance alone. The React and TypeScript frontend reaches the Spring Boot API through Nginx. The backend is divided into Account, Trade, Position, Market Data, and P&L modules. Inside each module, dependencies flow from API to application to domain, while infrastructure implements domain-facing ports. MySQL stores accounts, trades, and valuation snapshots and is the system of record. Redis caches market quotes only and can be discarded without losing unique business data. This boundary keeps deployment simple while preserving a path to future extraction.

## Slide 7 — 行情韧性与诚实降级 / Market-Data Resilience

### 中文

行情模块支持确定性的 Mock 和显式配置的 Finnhub。系统不会在真实行情失败时偷偷切换到 Mock，因为那会把演示数据误认为真实数据。正常请求先访问 provider，并把成功结果写入 Redis。缓存有两个时间窗口：新鲜期内可直接复用；超过新鲜期但仍在保留期内，只能在 provider 故障时作为 stale fallback。界面明确显示 MOCK、LIVE、CACHED、STALE 和 INCOMPLETE，让用户知道数据从哪里来、是否过期、估值是否完整。

### English

The market-data module supports deterministic Mock data and explicitly configured Finnhub data. It never silently switches from a failed real provider to Mock, because that would misrepresent generated values as market truth. A normal request calls the provider and stores a successful result in Redis. The cache has two windows: a fresh period for normal reuse, and a longer retention period that is available only as a stale fallback when the provider fails. The UI displays MOCK, LIVE, CACHED, STALE, and INCOMPLETE states so users know the origin, age, and completeness of every valuation.

## Slide 8 — Dashboard：从交易到组合洞察 / From Trades to Portfolio Insight

### 中文

Dashboard 是整个闭环的结果页。它可以按全部账户或单个账户查看，展示总市值、总成本、未实现盈亏、收益率、持仓数量和未定价持仓。下方逐个股票代码显示数量、平均成本、市场价、市值、盈亏和行情状态。这里的关键不是把数字画得漂亮，而是让每个指标都来自同一套后端计算，并且在行情陈旧或不完整时主动提示。估值历史只保存真实生成的快照，不会为了图表好看而伪造数据。

### English

The dashboard is the result page for the entire workflow. It can show all accounts or one selected account, with total market value, cost basis, unrealized P&L, return, open positions, and unpriced positions. The position table then exposes quantity, average cost, market price, market value, P&L, and quote status for each ticker. The important point is not visual decoration; it is that every metric comes from one backend calculation model and that stale or incomplete quotes are surfaced immediately. Valuation history contains only persisted snapshots—the system never fabricates history to make a chart look better.

## Slide 9 — 产品工作台 / Product Workbench

### 中文

除了 Dashboard，系统还有三个操作工作台。Accounts 管理多个证券账户及启用状态，并可查看账户级持仓。Activity 负责交易录入、分页查询、取消、审计保留的删除和修改。Market Data 提供股票搜索、单个或批量刷新、provider 状态以及演示环境下的故障模拟。桌面端和移动端共享同一套信息架构，关键状态使用文字标签而不只依赖颜色，方便测试和无障碍访问。

### English

Beyond the dashboard, the product has three operational workbenches. Accounts manages multiple securities accounts and their active state, with account-level position views. Activity supports trade entry, paginated search, cancellation, audit-preserving deletion, and amendment. Market Data provides instrument search, single and batch refresh, provider status, and controlled outage simulation in demo mode. Desktop and mobile layouts share one information architecture, and important states use text labels rather than color alone, which improves accessibility and makes behavior easier to test.

## Slide 10 — 工程质量与交付 / Engineering Quality and Delivery

### 中文

质量体系覆盖从领域规则到完整用户旅程。后端包含领域单元测试、API 集成测试、MySQL 和 Redis 的 Testcontainers 测试，以及 ArchUnit 架构约束。前端使用 Vitest、Testing Library 和 ESLint，并进行生产构建。Compose smoke test 验证生命周期、盈亏、缓存降级和容器重启；Playwright 在桌面和移动尺寸执行端到端流程。GitHub Actions 把 backend、frontend、compose-smoke 和 e2e 汇总到统一 quality gate，任何一项失败都不能通过。

### English

The quality strategy covers everything from domain rules to the complete user journey. The backend includes domain unit tests, API integration tests, MySQL and Redis Testcontainers tests, and ArchUnit dependency checks. The frontend uses Vitest, Testing Library, ESLint, and a production build. The Compose smoke test verifies lifecycle behavior, P&L, cache fallback, and container restart. Playwright runs the end-to-end workflow at both desktop and mobile sizes. GitHub Actions combines backend, frontend, compose-smoke, and E2E jobs into one quality gate, so no partial success can be reported as a passing build.

## Slide 11 — 已知边界与演进路线 / Boundaries and Roadmap

### 中文

当前版本有意控制范围：单用户、仅美元、没有现金账本、不允许做空，只计算未实现盈亏，并采用加权平均成本。这些不是隐藏缺陷，而是清晰的产品边界。下一阶段可以沿三条路线演进：第一，加入认证、授权和账户归属；第二，增加现金、多币种、已实现盈亏和税务批次；第三，引入更多行情源、WebSocket 实时推送、历史 K 线、生产级密钥管理和可观测性。现有模块边界为这些扩展提供了落点。

### English

The current release deliberately limits scope: it is single-user, USD-only, has no cash ledger, does not allow short positions, calculates unrealized P&L only, and uses weighted-average cost. These are explicit product boundaries rather than hidden defects. The next stage can evolve along three paths. First, add authentication, authorization, and account ownership. Second, add cash, multiple currencies, realized P&L, and tax-lot accounting. Third, introduce more quote providers, WebSocket streaming, historical candles, production secret management, and observability. The current module boundaries provide clear extension points for each path.

## Slide 12 — 总结与演示路径 / Takeaway and Demo Path

### 中文

总结来说，这个项目把交易事实、可重算持仓、可解释行情和一致的盈亏计算连接成一个可信闭环。它的三个核心价值是：业务规则可审计，外部故障可降级，交付结果可验证。现场演示可以按 Accounts 创建账户、Activity 录入买卖、Dashboard 查看盈亏、Market Data 模拟故障、再回到 Dashboard 观察 STALE 标记的顺序进行。谢谢大家，接下来欢迎提问。

### English

To conclude, this project connects trade facts, replayable positions, explainable market data, and consistent P&L into one trusted loop. Its three core values are auditable business rules, graceful external failure behavior, and verifiable delivery quality. A live demo can follow a simple path: create an account in Accounts, book buy and sell activity, inspect P&L on the Dashboard, simulate a provider outage in Market Data, and return to the Dashboard to observe the STALE status. Thank you, and we welcome your questions.
