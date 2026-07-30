# Equity Trade Booking Engine 超详细技术文档

> 文档语言：中文
>
> 文档类型：架构、开发、测试、部署与运维综合技术说明
>
> 代码基线：`feature/csv-import-registry` / `7d86610`
>
> 最后核对日期：2026-07-29
>
> 适用对象：开发人员、代码审查人员、测试人员、演示人员、运维人员和答辩评审

---

## 1. 文档目的

本文档描述 Equity Trade Booking Engine 的当前真实实现，而不是未来愿景。它回答以下问题：

1. 系统解决什么业务问题，明确不解决什么问题。
2. 后端、前端、MySQL、Redis、Finnhub 和 Docker 如何协作。
3. Account、Trade、Position、Market Data、P&L 和 Dashboard 的规则如何实现。
4. BUY、SELL、Cancel、Delete、Amend、CSV Import 的一致性如何保证。
5. 外部行情故障、Redis 故障、重复 CSV、并发 SELL 等异常如何处理。
6. 每个 REST API 的请求、响应、状态码和错误格式是什么。
7. Flyway V1–V7 如何演进数据库且保留历史数据。
8. 单元测试、集成测试、Compose smoke、Playwright E2E 和 CI 如何形成质量门禁。
9. 如何本地启动、演示、验证真实 Finnhub、排查故障并安全清理环境。

本文档以代码和配置为准。若本文档与运行中的 OpenAPI 不一致，应优先检查：

- 当前 Git 分支和 commit；
- `/v3/api-docs`；
- 对应 Controller、Application Service 和 Flyway migration；
- PR 是否与最新 `main` 发生了行为或文案变化。

---

## 2. 系统概览

### 2.1 产品定位

Equity Trade Booking Engine 是一个单用户、多证券账户、USD 股票交易登记系统。它不是交易所撮合引擎，也不是完整的个人财富管理平台。

系统的核心职责是：

- 管理多个证券账户；
- 录入 BUY/SELL 股票交易；
- 在 MySQL 中保存不可丢失的业务记录；
- 通过时间顺序重放交易计算 Position；
- 防止账户产生负持仓；
- 使用 Mock 或 Finnhub 行情计算 unrealized P&L；
- 使用 Redis 缓存行情并在 Provider 故障时使用保留缓存；
- 展示 Dashboard、近期 Activity 和估值历史；
- 保留取消、删除和修改操作的审计痕迹；
- 支持 CSV 批量录入和整表重复上传提醒。

### 2.2 当前业务边界

当前支持：

- 单用户；
- 多个证券账户；
- 每个账户固定使用 USD；
- BUY、SELL；
- 不允许做空；
- BOOKED、CANCELLED 两种交易状态；
- CANCELLED、DELETED、AMENDED 三种取消原因；
- weighted-average cost；
- unrealized P&L；
- Mock 和 Finnhub 行情；
- Redis quote cache；
- 估值快照及按日历史曲线；
- 中、英、葡三种前端语言。

当前不支持：

- 登录、权限和多用户数据隔离；
- 现金余额和双重记账；
- realized P&L；
- FIFO/LIFO tax lots；
- 多币种和外汇换算；
- short selling、margin；
- 订单撮合、部分成交、限价单、止损单；
- 实时 WebSocket 行情；
- 分红、拆股、公司行动；
- 交易物理删除；
- 微服务、Kafka、Event Sourcing。

### 2.3 关键数据归属

| 数据 | 权威存储 | 是否可重建 | Redis 是否保存唯一副本 |
| --- | --- | --- | --- |
| Account | MySQL `accounts` | 否 | 否 |
| Trade | MySQL `trades` | 否 | 否 |
| CSV Import Registry | MySQL `trade_import_registry` | 否 | 否 |
| Position | 从 BOOKED Trade 计算 | 是 | 否 |
| Current P&L | 从 Position + Quote 计算 | 是 | 否 |
| Valuation Snapshot | MySQL `valuation_snapshots` | 部分可重算 | 否 |
| Market Quote | Redis | Provider 可重新获取 | 是，但仅为缓存数据 |

MySQL 是业务系统的 system of record。Redis 仅用于 Market Quote，不参与 Account、Trade、Position 或审计记录的持久化。

---

## 3. 技术栈

### 3.1 后端

| 技术 | 当前版本/来源 | 用途 |
| --- | --- | --- |
| Java | 21 | 后端语言 |
| Spring Boot | 3.5.14 | 应用框架及依赖管理 |
| Spring MVC | Spring Boot 管理 | REST API |
| Spring Data JPA | Spring Boot 管理 | MySQL persistence adapter |
| Hibernate | Spring Boot 管理 | ORM，`ddl-auto=validate` |
| Flyway | Spring Boot 管理 | Schema migration |
| Spring Data Redis | Spring Boot 管理 | Redis quote cache |
| Java `HttpClient` | JDK 21 | Finnhub HTTP adapter |
| springdoc-openapi | 2.8.17 | OpenAPI 和 Swagger UI |
| Maven Wrapper | 仓库内置 | 可重复构建 |
| JUnit Jupiter | Spring Boot test BOM | 测试 |
| Testcontainers | Spring Boot dependency management | MySQL/Redis 集成测试 |
| ArchUnit | 1.4.1 | 架构依赖约束 |

### 3.2 前端

| 技术 | 当前版本 | 用途 |
| --- | --- | --- |
| React | 19.2.x | UI |
| TypeScript | 6.0.x | 类型系统 |
| Vite | 8.1.x | 开发和构建 |
| Vitest | 4.1.x | 单元/组件测试 |
| Testing Library | 16.3.x | 用户视角组件测试 |
| Playwright | 1.62.0 | 浏览器 E2E |
| ESLint | 10.x | 静态检查 |
| Nginx | 1.28 Alpine image | 静态文件和 `/api` 代理 |

项目没有引入 Redux、MobX、Zustand 或大型设计系统。页面状态使用 React hooks 管理，HTTP 调用集中在 `frontend/src/api.ts`。

### 3.3 基础设施

| 服务 | 固定版本 | 用途 |
| --- | --- | --- |
| MySQL | 8.4 | 权威业务数据库 |
| Redis | 7.4.2 Alpine | 行情 JSON 缓存 |
| Docker Compose | Compose plugin | 本地、CI、Demo 编排 |
| GitHub Actions | 固定 major action tag | 自动质量门禁 |

---

## 4. 总体架构

### 4.1 运行时视图

```text
┌─────────────────────────────────────────────────────────────────┐
│ Browser                                                         │
│ React + TypeScript                                              │
│ Dashboard / Accounts / Activity / Market Data                   │
└───────────────────────────────┬─────────────────────────────────┘
                                │ HTTP / JSON
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│ Nginx Frontend Container                                        │
│ /              -> React static assets                           │
│ /api/*         -> backend:8080                                  │
└───────────────────────────────┬─────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│ Spring Boot Modular Monolith                                    │
│                                                                 │
│ Account │ Trade/Activity │ Position │ Market Data │ P&L/Dashboard│
│                                                                 │
│ API -> Application -> Domain <- Infrastructure                  │
└───────────────┬──────────────────────────┬──────────────────────┘
                │                          │
                ▼                          ▼
┌──────────────────────────┐   ┌──────────────────────────────────┐
│ MySQL 8.4                │   │ Redis 7.4.2                      │
│ accounts                 │   │ market:quote:{TICKER}            │
│ trades                   │   │ disposable quote JSON            │
│ valuation_snapshots      │   └──────────────────────────────────┘
│ trade_import_registry    │                  ▲
└──────────────────────────┘                  │ cache miss/stale
                                             ▼
                                  ┌───────────────────────────────┐
                                  │ MarketDataProvider            │
                                  │ Mock 或 Finnhub REST API       │
                                  └───────────────────────────────┘
```

### 4.2 模块内依赖方向

每个业务模块遵守：

```text
api → application → domain ← infrastructure
```

含义：

- `api`：HTTP 路由、请求/响应 DTO、状态码、OpenAPI 注解；
- `application`：用例编排、事务边界、跨 domain port 协作；
- `domain`：业务模型、算法、Repository/Provider port；
- `infrastructure`：JPA、Spring Data、Redis、Finnhub、scheduler 等实现。

禁止的依赖方向由 ArchUnit 自动保护：

- domain 不依赖 api/application/infrastructure；
- domain 不依赖 Spring；
- domain 不依赖 Jakarta Persistence；
- domain 不依赖 Jackson；
- Market Data domain/application 不依赖 HTTP 或 Finnhub adapter；
- P&L domain 不依赖 JPA、Redis、React 或框架；
- API 不直接依赖 persistence implementation。

### 4.3 模块间协作

```text
Account
  ├─ Trade booking 校验账户存在和 ACTIVE 状态
  └─ Dashboard 提供账户数量及筛选上下文

Trade
  ├─ Position 重放 BOOKED trades
  ├─ Dashboard recent activity
  ├─ Historical valuation replay
  └─ CSV import 最终逐笔调用 Trade API

Position
  ├─ Market Data 批量接口提取当前 ticker
  └─ P&L 提供 quantity / averageCost / costBasis

Market Data
  ├─ Redis 缓存 quote
  ├─ Mock/Finnhub provider
  └─ P&L 提供 marketPrice 和 quote 状态

P&L/Dashboard
  ├─ 组合 Position 与 Quote
  ├─ 生成 current metrics
  ├─ 保存 valuation snapshot
  └─ 生成历史曲线
```

---

## 5. 仓库结构

```text
equity_trade_booking_engine/
├── .github/workflows/ci.yml       # GitHub Actions
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/equitytrade/booking/
│       │   ├── account/
│       │   ├── trade/
│       │   ├── position/
│       │   ├── marketdata/
│       │   ├── pnl/
│       │   └── documentation/
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/V1...V7
│       └── test/
├── frontend/
│   ├── src/
│   │   ├── api.ts
│   │   ├── account/
│   │   ├── trade/
│   │   ├── market/
│   │   ├── dashboard/
│   │   └── i18n.tsx
│   ├── e2e/final-demo.spec.ts
│   ├── playwright.config.ts
│   └── nginx.conf
├── scripts/
│   ├── ci-smoke.sh
│   ├── ci-e2e.sh
│   ├── demo-up.sh
│   ├── demo-seed.sh
│   ├── demo-down.sh
│   ├── verify-finnhub-live.sh
│   └── finnhub-stub.py
├── compose.yaml
├── .env.example
├── start.sh
└── README.md
```

---

## 6. Account 模块

### 6.1 Account 数据

Account 包含：

- `id`：UUID，MySQL 使用 `CHAR(36)`；
- `name`：必填、唯一、最多 100；
- `broker`：必填、最多 100；
- `accountNumberLast4`：可空；非空时必须为四位数字；
- `baseCurrency`：固定 `USD`；
- `status`：`ACTIVE` 或 `INACTIVE`；
- `createdAt`、`updatedAt`：UTC 微秒精度。

### 6.2 Account 生命周期

```text
Create
  └─> ACTIVE
       ├─> Update name/broker/last4
       └─> Deactivate
             └─> INACTIVE
```

规则：

- Account 不物理删除；
- deactivate 幂等；
- INACTIVE Account 仍可查询；
- INACTIVE Account 的历史 Trade、Position、P&L 仍可查询；
- 不能向 INACTIVE Account 创建新 Trade；
- Account name 唯一冲突返回 409。

### 6.3 Primary Account

V3 migration 固定创建：

```text
id      = 00000000-0000-0000-0000-000000000001
name    = Primary Account
broker  = Legacy
status  = ACTIVE
currency= USD
```

V3 会将 V2 时代所有没有 `account_id` 的历史 Trade 回填到 Primary Account，然后才将 `account_id` 改为 `NOT NULL` 并创建外键。因此升级不会丢失旧交易。

---

## 7. Trade / Activity 模块

### 7.1 Trade 字段

| 字段 | 说明 |
| --- | --- |
| `id` | UUID / `CHAR(36)` |
| `accountId` | Account 外键 |
| `ticker` | 标准化为大写，最长 10 |
| `side` | BUY 或 SELL |
| `quantity` | `DECIMAL(19,6)`，大于 0 |
| `tradePrice` | `DECIMAL(19,6)`，大于 0 |
| `executedAt` | 实际执行时间，UTC 微秒 |
| `status` | BOOKED 或 CANCELLED |
| `createdAt` | 系统录入时间，UTC 微秒 |
| `cancelledAt` | 首次取消时间 |
| `cancellationReason` | CANCELLED / DELETED / AMENDED |
| `supersedesTradeId` | Amendment 新交易指向旧交易 |

### 7.2 创建交易

请求进入 `TradeController`，被转换为 `BookTradeCommand`。Application Service 的处理顺序是：

1. 使用注入的 `Clock` 创建 Domain Trade；
2. 校验：
   - accountId 必填；
   - ticker 必填并使用 `Locale.ROOT` 大写；
   - ticker 匹配 `[A-Z][A-Z0-9.-]{0,9}`；
   - side 为 BUY/SELL；
   - quantity、tradePrice 大于 0；
   - 最多 6 位小数；
   - 最多 13 位整数；
   - executedAt 不超过服务器时间 60 秒；
3. 通过 `TradeInstrumentValidator` 验证 ticker 是支持的证券；
4. 开启事务；
5. 对 Account 行加数据库写锁；
6. 验证 Account 存在且 ACTIVE；
7. SELL 时将 proposed Trade 插入历史序列并重放；
8. 重放任意时点不为负才保存；
9. 返回 HTTP 201。

### 7.3 并发超卖保护

SELL 不能只在内存中检查当前 Position，因为两个请求可能同时读取相同余额。

当前实现使用：

```text
SELECT Account FOR UPDATE
```

语义上通过 `AccountRepository.findByIdForUpdate(accountId)` 获取账户行锁。相同 Account 的 SELL、Cancel、Delete 和 Amendment 被串行化。锁内重新读取交易序列并验证，确保两个并发 SELL 不会同时消耗同一份 Position。

不同 Account 可以并行处理，不会使用全局锁。

### 7.4 时间顺序和 backdated SELL

重放顺序固定为：

```text
executedAt ASC
createdAt ASC
id ASC
```

这保证：

- SELL 只能使用其执行时间之前已存在的 BUY；
- 未来 BUY 不能支撑 backdated SELL；
- 相同 executedAt 使用系统记录时间确定顺序；
- createdAt 也相同时使用 UUID 形成稳定 tie-breaker。

### 7.5 Cancel

`POST /api/trades/{id}/cancel`

- BOOKED → CANCELLED；
- 设置第一次 `cancelledAt`；
- 设置 `cancellationReason=CANCELLED`；
- 重复普通 Cancel 返回原 Trade，不改第一次 cancelledAt；
- 取消 SELL 会恢复 Position；
- 取消 BUY 前会移除该 BUY 并重放剩余时间线；
- 如果移除 BUY 会使后续 SELL 在任意时间点变成负持仓，返回 409。

### 7.6 Delete

`DELETE /api/trades/{id}` 不是物理删除。

它执行：

```text
BOOKED -> CANCELLED
cancellationReason = DELETED
```

记录仍在 `trades` 表中、仍可浏览、仍可审计，但不再参与 Position 和 P&L。

Delete 对相同原因幂等。已经因其他原因取消的交易不能被重新标记为 DELETED，会返回 409。

### 7.7 Amend

`POST /api/trades/{id}/amend`

Amend 在一个事务中执行：

1. 查找原 Trade；
2. 按 UUID 排序锁定原账户和新账户，避免跨账户死锁；
3. 校验原 Trade 仍是 BOOKED；
4. 校验新 Account ACTIVE；
5. 禁止“所有字段完全相同”的空修改；
6. 分别重放原 key 和新 key 的交易序列；
7. 原 Trade 标记为 CANCELLED + AMENDED；
8. 创建 replacement Trade；
9. replacement 的 `supersedesTradeId` 指向原 Trade。

数据库对 `supersedes_trade_id` 有唯一索引，防止多个 replacement 同时宣称替代同一 Trade。

### 7.8 Activity 查询

`GET /api/trades`

参数：

- `accountId`：可选；
- `page`：默认 0；
- `size`：默认 10，范围 1–100。

排序不可由客户端控制：

```text
executedAt DESC
createdAt DESC
id DESC
```

返回：

```json
{
  "items": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0
}
```

---

## 8. CSV 批量导入和重复整表检测

### 8.1 CSV 格式

必需列：

```csv
account,ticker,side,quantity,tradePrice,executedAt
Primary Account,AAPL,BUY,10,195.25,2026-07-28T10:00:00Z
```

前端限制：

- `.csv` 文件；
- 最大 1,000,000 bytes；
- 最大 200 行；
- account 可使用 ACTIVE Account name 或 UUID；
- ticker 格式校验；
- BUY/SELL；
- quantity 和 tradePrice 为正数且最多 6 位小数；
- executedAt 必须包含 `Z` 或 UTC offset；
- executedAt 不超过当前时间 60 秒；
- 任一行结构校验失败时，不开始批量导入。

### 8.2 内容身份规范化

重复判断不是基于文件名，而是基于业务内容。

每行规范化为：

```json
{
  "accountId": "lowercase-uuid",
  "ticker": "UPPERCASE",
  "side": "UPPERCASE",
  "quantity": "canonical decimal",
  "tradePrice": "canonical decimal",
  "executedAt": "canonical UTC timestamp"
}
```

然后：

1. 每行 JSON 序列化；
2. 按字符串排序；
3. 使用换行连接；
4. 浏览器 Web Crypto 计算 SHA-256；
5. 发送 64 位小写 hex hash 到后端。

因此以下变化不会绕过重复提示：

- 文件改名；
- 行顺序变化；
- Header 大小写或顺序变化；
- `1.0` 与 `1.000000`；
- 表示相同 UTC 时刻的 offset 格式。

以下变化会生成不同身份：

- Account 不同；
- ticker、side、quantity、price 或 executedAt 业务值不同；
- 行被增加或删除。

### 8.3 Import UUID

后端从 SHA-256 的前 16 bytes 派生稳定 UUID，并设置 RFC variant 和 version 8 bits。

```text
SHA-256 canonical table
  └─ first 128 bits
       └─ deterministic UUID
```

这个 UUID 表示“规范化后的整张表”，不是某次上传文件实例。

### 8.4 首次和重复导入流程

首次：

```text
Parse CSV
  -> Hash
  -> POST registration repeatConfirmed=false
  -> 201 IN_PROGRESS
  -> POST /api/trades for every row
  -> PATCH result
  -> COMPLETED / PARTIAL / FAILED
```

重复且未确认：

```text
POST registration repeatConfirmed=false
  -> 409 Problem Details
  -> duplicateImport metadata
  -> UI alertdialog
       ├─ Cancel: 不发送任何 Trade
       └─ Import again:
            POST repeatConfirmed=true
            导入整张表
```

### 8.5 Registry 状态

| 状态 | 含义 |
| --- | --- |
| IN_PROGRESS | 当前一次导入已登记，结果尚未记录 |
| COMPLETED | 全部行成功 |
| PARTIAL | 部分成功、部分失败 |
| FAILED | 全部行失败 |

`importCount` 每次明确确认重复导入时加一。完成结果必须携带当前 importCount，避免旧请求覆盖新一轮结果。

### 8.6 并发登记

数据库对 `content_hash` 有唯一约束。Application Service 同时处理：

- 正常 pessimistic lock 路径；
- 并发首次 INSERT 的 `DataIntegrityViolationException`；
- MySQL transient/deadlock 异常。

发生竞争后，服务在新事务中重新读取已持久化 Registry，并按“拒绝或确认重复”规则处理。

---

## 9. Position 计算

### 9.1 Position 不是数据库表

Position 是从 BOOKED Trade 动态计算的 projection：

```text
Position Key = accountId + ticker
```

CANCELLED Trade 完全排除。quantity=0 的 Position 不返回。

### 9.2 Weighted-average cost

内部使用 `MathContext.DECIMAL128`。

BUY：

```text
newQuantity  = oldQuantity + buyQuantity
newCostBasis = oldCostBasis + buyQuantity × buyPrice
averageCost  = newCostBasis / newQuantity
```

SELL：

```text
averageCost  = oldCostBasis / oldQuantity
newQuantity  = oldQuantity - sellQuantity
newCostBasis = newQuantity × averageCost
```

SELL 不使用卖出价改变 remaining average cost。卖出价将来可用于 realized P&L，但当前没有 realized P&L 模块。

### 9.3 聚合 Position

- `GET /api/positions?accountId=...`：单账户；
- `GET /api/accounts/{accountId}/positions`：单账户别名入口；
- `GET /api/positions`：所有账户，相同 ticker 聚合。

全账户聚合时 response 中 `accountId` 可为 `null`。结果按 ticker ASC。

---

## 10. Market Data 模块

### 10.1 Port 与 Adapter

Domain ports：

- `MarketDataProvider`；
- `MarketDataCache`；
- `InstrumentSearchProvider`；
- `HistoricalMarketDataProvider`；
- `PositionTickerSource`；
- `MarketDataProviderState`；
- `DemoMarketDataControl`。

Infrastructure adapters：

- `DeterministicMockMarketDataProvider`；
- `FinnhubMarketDataProvider`；
- `RedisMarketDataCache`；
- Finnhub instrument search；
- Finnhub historical candle；
- Position ticker adapter；
- observed provider/runtime state；
- demo outage decorator/control。

Application 和 Domain 不直接依赖 RedisTemplate、Finnhub DTO 或 Java HTTP 客户端。

### 10.2 Redis key 和 JSON

Key：

```text
market:quote:{TICKER}
```

例如：

```text
market:quote:AAPL
```

Value 是 `MarketQuote` JSON：

```json
{
  "ticker": "AAPL",
  "price": 195.25,
  "previousClose": 193.80,
  "marketTimestamp": "2026-07-29T10:00:00Z",
  "fetchedAt": "2026-07-29T10:00:01Z",
  "source": "FINNHUB",
  "mock": false
}
```

Redis 保存 domain quote；`cached`、`stale` 是读取时计算的响应状态，不写入 quote value。

### 10.3 双 TTL 语义

- Fresh TTL 默认 60 秒：由 Application Service 比较 `fetchedAt` 计算；
- Retention TTL 默认 24 小时：Redis key 的真实过期时间。

为什么不是直接让 Redis 60 秒过期：

- 60 秒内直接 cache hit；
- 60 秒后 quote 被视为 stale candidate，尝试 Provider；
- Provider 成功则覆盖缓存；
- Provider 失败仍可返回保留的旧 quote；
- 24 小时后旧 quote 从 Redis 消失，Provider 失败时返回 503。

必须满足：

```text
retention TTL > fresh TTL > 0
```

### 10.4 Quote 查询状态机

```text
GET quote
  |
  ├─ Redis 有 fresh quote 且非强制 refresh
  │    └─ 返回 cached=true
  |
  └─ miss / stale / force refresh
       |
       ├─ Provider success
       │    ├─ 尝试写 Redis
       │    └─ 返回 cached=false, stale=false
       |
       └─ Provider failure
            ├─ 有 retained quote
            │    └─ 返回 cached=true, stale=true
            └─ 无 retained quote
                 ├─ no data -> 404
                 └─ timeout/rate/auth/5xx -> 503
```

### 10.5 Redis 故障行为

`RedisMarketDataCache` 捕获 Redis read/write failure：

- read failure：记录安全 warning，表现为 cache miss；
- write failure：记录安全 warning，但仍返回 Provider 的成功 quote；
- 不把 Redis exception 传播到 Account、Trade 或 Position；
- 不记录原始缓存内容或 secret。

精确限制：

- Redis 不可用但 Provider 可用：Market Data 仍可工作，但每次可能重新请求 Provider；
- Redis 不可用且 Provider 也失败：无法取得 retained quote，返回 503；
- Redis 恢复后，下一次 Provider success 会重新建立缓存；
- MySQL 中的业务数据和估值记录不受 Redis 删除影响。

### 10.6 Mock Provider

已知 ticker 固定基础值：

- AAPL；
- MSFT；
- NVDA；
- GOOGL；
- AMZN。

未知但格式合法的 ticker：

1. 使用 ticker hash 生成 20.00–499.99 范围的 previousClose；
2. 根据 ticker hash 和时间窗口生成 ±250 basis points 变化；
3. 同 ticker 在同一窗口内稳定；
4. price 和 previousClose 大于 0；
5. 最多 6 位小数；
6. `source=MOCK`、`mock=true`。

Mock 是演示数据，不是真实或延迟行情。

### 10.7 Finnhub Provider

Quote endpoint：

```text
GET {FINNHUB_BASE_URL}/quote?symbol=AAPL
X-Finnhub-Token: <secret>
```

映射：

| Finnhub 字段 | Domain 字段 |
| --- | --- |
| `c` | price |
| `pc` | previousClose |
| `t` | marketTimestamp，Unix seconds |

校验：

- `c > 0`；
- `pc > 0`；
- `t > 0` 且可转换为 Instant；
- JSON 必须可解析；
- 无效响应不写 Redis。

API key 只放在 `X-Finnhub-Token` header，不放 URL、响应、状态接口或日志。

### 10.8 Retry 分类

| 类型 | 是否重试 | 最终无缓存状态 |
| --- | --- | --- |
| Connect failure | 最多重试一次 | 503 |
| Timeout | 最多重试一次 | 503 |
| HTTP 5xx | 最多重试一次 | 503 |
| HTTP 429 | 不重试 | 503 |
| HTTP 400/404 | 不重试 | 404 |
| HTTP 401/403 | 不重试 | 503，安全配置错误 |
| Malformed JSON | 不重试 | 503 |
| c/pc 为 0 | 不重试 | 404 |

`MARKET_DATA_MAX_ATTEMPTS` 只能是 1 或 2。

### 10.9 Provider 状态

`GET /api/market-data/provider/status` 返回：

```json
{
  "provider": "FINNHUB",
  "configured": true,
  "demoControlsEnabled": true,
  "demoOutageEnabled": false,
  "lastSuccessAt": "2026-07-29T10:00:00Z",
  "lastFailureAt": null,
  "lastFailureCategory": null
}
```

不会返回：

- API key；
- Authorization/Header；
- 敏感 URL 参数；
- 原始 exception；
- response body。

### 10.10 Demo outage

只有以下条件同时满足才启用：

```text
MARKET_DATA_PROVIDER=finnhub
MARKET_DATA_DEMO_CONTROLS_ENABLED=true
```

Endpoints：

- `GET /api/demo/market-data/outage`；
- `POST /api/demo/market-data/outage/enable`；
- `POST /api/demo/market-data/outage/disable`。

关闭配置时这些 endpoint 返回 404。Outage 状态仅保存在内存，后端重启后恢复 disabled。它只阻断外部 Provider，不删除 Redis，因此适合演示 stale fallback。

---

## 11. Unrealized P&L

### 11.1 公式

每个 Position：

```text
marketValue   = quantity × marketPrice
unrealizedPnl = marketValue - costBasis
pnlPercent    = unrealizedPnl / costBasis × 100
```

后端使用 `BigDecimal` + `MathContext.DECIMAL128`，API 输出最多 6 位小数。

### 11.2 缺失行情

如果某个 Position 没有可用 quote：

- `marketPrice=null`；
- `marketValue=null`；
- `unrealizedPnl=null`；
- `pnlPercent=null`；
- `available=false`；
- 不使用 0 冒充缺失价格。

Totals 只汇总 available Position：

- `positionCount`：全部非零 Position；
- `pricedPositionCount`：有 quote；
- `unpricedPositionCount`：无 quote；
- `complete`：`unpricedPositionCount == 0`；
- `mock`：任一 priced item 为 Mock；
- `stale`：任一 priced item 为 stale。

如果 costBasis 为 0，percent 返回 `null`，避免除零。

### 11.3 All Accounts

不传 accountId 时，相同 ticker 的 Position 先按 weighted cost 汇总，再计算该 ticker P&L。不会简单平均各账户的 averageCost。

---

## 12. Dashboard 和估值历史

### 12.1 Current Dashboard

`GET /api/dashboard` 返回：

- P&L totals；
- Position P&L items；
- Account count；
- Active Account count；
- 最近 5 条 Activity；
- quote status counts；
- capturedAt。

### 12.2 Refresh

`POST /api/dashboard/refresh`

流程：

1. 获取当前 Position；
2. 对每个 distinct ticker 强制刷新；
3. 单 ticker 失败不会让其他 ticker 全部失败；
4. retained quote 可作为 stale；
5. 计算 P&L；
6. 保存 scope=ALL 或 ACCOUNT 的 valuation snapshot；
7. 如果刷新 ALL，还为每个账户保存同一 capturedAt 的账户快照；
8. 不修改任何 Trade 或 Position。

### 12.3 Valuation Snapshot

`valuation_snapshots` 保存总体值，不复制 Trade：

- scope_type：ALL / ACCOUNT；
- account_id；
- total_cost_basis；
- total_market_value；
- unrealized_pnl；
- position_count；
- priced_position_count；
- complete/mock/stale；
- captured_at。

### 12.4 Scheduler

默认：

```text
enabled       = true
interval      = 15m
initial delay = 15m
```

Scheduler 为 ALL 和每个 Account 保存快照。测试和 CI 关闭 scheduler，避免异步写入导致随机测试结果。

### 12.5 历史曲线

Endpoint：

```text
GET /api/dashboard/history?range=1D|7D|30D|ALL
GET /api/dashboard/history?accountId=<uuid>&range=30D
```

当前历史曲线按 BOOKED ledger 和 historical daily closes 重建：

- 使用 executedAt 决定某日持仓；
- CANCELLED、DELETED、AMENDED 原记录被排除；
- 周末和休市日 carry forward 最近可用 close；
- 缺少历史 close 时该日 `complete=false`；
- 不生成虚假的历史数据；
- 结果按日期 ASC。

Finnhub `/stock/candle` 可能依赖付费 entitlement。无权限时历史曲线可能 incomplete，但当前 quote 仍可工作。

---

## 13. 数据库与 Flyway

### 13.1 Migration 原则

- V1–V7 不应被修改；
- 新 schema 变化必须新增 V8+；
- 禁止在正常环境运行 `flyway clean`；
- Hibernate 只负责 validate，不负责建表；
- 时间使用 UTC；
- UUID 明确为 `CHAR(36)`；
- MySQL storage engine 为 InnoDB。

### 13.2 Migration 历史

| Migration | 内容 |
| --- | --- |
| V1 | 空 baseline |
| V2 | 创建 BUY-only trades |
| V3 | accounts、Primary Account、历史 Trade 回填、外键 |
| V4 | 开放 SELL/CANCELLED、cancelled_at、Position replay index |
| V5 | valuation_snapshots |
| V6 | cancellation reason、amend audit link |
| V7 | CSV import registry |

### 13.3 主要约束

`accounts`：

- unique name；
- last4 为 null 或四位数字；
- currency=USD；
- status ACTIVE/INACTIVE。

`trades`：

- side BUY/SELL；
- status BOOKED/CANCELLED；
- quantity/price > 0；
- BOOKED 不允许 cancellation audit data；
- CANCELLED 必须有 cancelled_at 和 reason；
- account FK；
- supersedes self FK；
- supersedes unique。

`valuation_snapshots`：

- ALL scope 时 account null；
- ACCOUNT scope 时 account not null；
- counts 非负且 priced <= total；
- account FK。

`trade_import_registry`：

- content_hash unique；
- row count 1–200；
- import count > 0；
- result count 有效；
- status 枚举约束。

### 13.4 索引

| 索引 | 用途 |
| --- | --- |
| `idx_trades_executed_at` | executedAt 查询 |
| `idx_trades_ticker` | ticker 查询 |
| `idx_trades_account_id` | account filter/FK |
| `idx_trades_position_replay` | account+ticker+status 时间重放 |
| `uk_trades_supersedes_trade` | amendment 一对一 |
| snapshot scope/account/captured index | 历史范围 |
| registry last_imported_at | Import audit |

---

## 14. REST API 总表

### 14.1 系统和文档

| Method | Path | 用途 |
| --- | --- | --- |
| GET | `/api/health` | Actuator health |
| GET | `/v3/api-docs` | OpenAPI JSON |
| GET | `/swagger-ui.html` | Swagger UI |

### 14.2 Account API

| Method | Path | 成功状态 |
| --- | --- | --- |
| POST | `/api/accounts` | 201 |
| GET | `/api/accounts` | 200 |
| GET | `/api/accounts/{id}` | 200 |
| PATCH | `/api/accounts/{id}` | 200 |
| POST | `/api/accounts/{id}/deactivate` | 200 |

创建示例：

```json
{
  "name": "Primary Brokerage",
  "broker": "Example Broker",
  "accountNumberLast4": "4242"
}
```

### 14.3 Trade API

| Method | Path | 用途 |
| --- | --- | --- |
| POST | `/api/trades` | BOOK BUY/SELL |
| GET | `/api/trades` | 分页浏览 Activity |
| POST | `/api/trades/{id}/cancel` | 普通取消 |
| DELETE | `/api/trades/{id}` | 审计保留删除 |
| POST | `/api/trades/{id}/amend` | 原交易取消 + replacement |

创建示例：

```json
{
  "accountId": "bb06cce4-21c1-45ce-9bb4-0ebc6b96326c",
  "ticker": "AAPL",
  "side": "BUY",
  "quantity": 10.000000,
  "tradePrice": 195.250000,
  "executedAt": "2026-07-28T10:00:00Z"
}
```

### 14.4 CSV Registry API

| Method | Path | 用途 |
| --- | --- | --- |
| POST | `/api/trade-imports/registrations` | 登记/检测重复 |
| PATCH | `/api/trade-imports/{importId}/result` | 记录导入结果 |

### 14.5 Position API

| Method | Path |
| --- | --- |
| GET | `/api/positions` |
| GET | `/api/positions?accountId={uuid}` |
| GET | `/api/accounts/{accountId}/positions` |

### 14.6 Market Data API

| Method | Path |
| --- | --- |
| GET | `/api/market-data/quotes/{ticker}` |
| POST | `/api/market-data/quotes/{ticker}/refresh` |
| GET | `/api/market-data/quotes` |
| GET | `/api/market-data/quotes?accountId={uuid}` |
| GET | `/api/market-data/instruments/search?q=AAPL&limit=10` |
| GET | `/api/market-data/provider/status` |

批量 quote 只返回当前非零 BOOKED Position 中的 ticker，按 ticker ASC。

### 14.7 P&L 和 Dashboard API

| Method | Path |
| --- | --- |
| GET | `/api/pnl` |
| GET | `/api/pnl?accountId={uuid}` |
| GET | `/api/dashboard` |
| GET | `/api/dashboard?accountId={uuid}` |
| POST | `/api/dashboard/refresh` |
| POST | `/api/dashboard/refresh?accountId={uuid}` |
| GET | `/api/dashboard/history?range=30D` |

### 14.8 Demo API

| Method | Path |
| --- | --- |
| GET | `/api/demo/market-data/outage` |
| POST | `/api/demo/market-data/outage/enable` |
| POST | `/api/demo/market-data/outage/disable` |

---

## 15. Problem Details

错误响应：

```text
Content-Type: application/problem+json
```

标准结构：

```json
{
  "type": "urn:equity-trade:problem:validation",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/trades",
  "errors": {
    "quantity": "must be greater than 0"
  }
}
```

Problem categories：

| type | HTTP | 场景 |
| --- | --- | --- |
| `urn:equity-trade:problem:validation` | 400 | 字段、UUID、分页、range |
| `urn:equity-trade:problem:not-found` | 404 | Account/Trade/Quote 不存在 |
| `urn:equity-trade:problem:conflict` | 409 | inactive、超卖、取消冲突、重复 CSV |
| `urn:equity-trade:problem:market-data-unavailable` | 503 | Provider 无缓存降级 |

安全原则：

- instance 仅使用 request path；
- 不包含主机、query secret；
- 不返回 Java class name；
- 不返回 SQL；
- 不返回 stack trace；
- 不返回原始 Provider body；
- duplicate CSV 使用扩展属性 `duplicateImport`，只包含安全 Import metadata。

---

## 16. 前端架构

### 16.1 页面

顶层页面：

- Dashboard；
- Accounts；
- Activity（最新 main 的文案可能显示 Trade）；
- Market Data。

桌面使用侧边导航；移动端使用可展开侧边栏、遮罩、Escape 关闭和焦点恢复。

### 16.2 API 集中管理

所有业务 HTTP 调用集中在：

```text
frontend/src/api.ts
```

组件不直接散落新的 `fetch`。`request<T>` 统一：

- 发送请求；
- JSON decode；
- 非 2xx 转成 `ApiProblemError`；
- 保留 Problem Details `errors`；
- 由页面映射 field/server error。

### 16.3 前端状态

每个页面独立维护：

- loading；
- empty；
- success；
- field error；
- server error；
- action pending；
- selected account/filter；
- pagination/range。

没有全局状态管理库。

### 16.4 国际化

`i18n.tsx` 支持：

- English；
- 简体中文；
- Português do Brasil。

包括导航、表单、状态、CSV 文件选择、重复上传对话框和错误提示。

自定义 CSV file control 避免浏览器原生“选择文件”文案受操作系统语言影响：

- English：Choose file / No file selected；
- 中文：选择文件 / 未选择文件；
- Português：Escolher arquivo / Nenhum arquivo selecionado。

### 16.5 Accessibility

实现点：

- 导航使用 button 和 `aria-current=page`；
- 移动菜单有 `aria-expanded`、`aria-controls`；
- 表单使用可访问 label；
- duplicate confirmation 使用 alertdialog；
- loading 时按钮 disabled；
- P&L 不只使用红绿颜色，同时显示正负号和 Gain/Loss/Flat；
- Playwright 检查键盘导航和水平溢出。

### 16.6 金额精度的真实现状

后端核心计算使用 BigDecimal，是当前权威计算结果。

但当前前端 TypeScript API 类型将 `quantity`、`price`、`marketValue` 等声明为 JavaScript `number`，JSON 也通过原生 `response.json()` 解析。因此：

- 一般展示和当前测试范围内的数值正确；
- 对接近数据库最大范围的极大金额，浏览器可能存在 IEEE-754 精度风险；
- 前端不能被视为核心金额计算的权威来源；
- 若未来要严格支持极大金额，应将 financial decimal 作为 JSON string，并使用 decimal library 或字符串格式化；
- 该改动需要 API contract、所有前端类型和测试同步升级。

---

## 17. Docker Compose

### 17.1 服务依赖

```text
db healthy ─────┐
                ├─> backend healthy ─> frontend
redis healthy ──┘

ci-finnhub profile:
finnhub-stub healthy ─> backend via FINNHUB_BASE_URL
```

### 17.2 默认端口

| 服务 | 容器端口 | 默认宿主端口 |
| --- | --- | --- |
| Frontend | 80 | 3000 |
| Backend | 8080 | 8080 |
| MySQL | 3306 | 3307 |
| Redis | 6379 | 6379 |

### 17.3 Named Volumes

默认开发：

- `<compose-project>_mysql_data`；
- `<compose-project>_redis_data`。

正常停止：

```bash
docker compose down
```

该命令保留 volume。

只有明确要销毁隔离 CI/Demo 环境时才使用对应脚本中的 `down -v`。不要对默认开发项目盲目执行 `docker compose down -v`。

### 17.4 Healthcheck

- MySQL：`mysqladmin ping`；
- Redis：`redis-cli ping`；
- Backend：`GET /api/health` 且 status UP；
- Finnhub stub：`GET /health`。

---

## 18. 配置

### 18.1 数据库

| 环境变量 | 默认 |
| --- | --- |
| `DB_URL` | localhost:3307/equity_booking + UTC |
| `DB_USERNAME` | equity_app |
| `DB_PASSWORD` | local_app_password |
| `MYSQL_ROOT_PASSWORD` | local_root_password |

生产环境必须替换默认密码。

### 18.2 Redis

| 环境变量 | 默认 |
| --- | --- |
| `REDIS_HOST` | localhost |
| `REDIS_PORT` | 6379 |
| `REDIS_CONNECT_TIMEOUT` | 1s |
| `REDIS_COMMAND_TIMEOUT` | 1s |

### 18.3 Market Data

| 环境变量 | 默认 |
| --- | --- |
| `MARKET_DATA_PROVIDER` | mock |
| `MARKET_DATA_FRESH_TTL` | 60s |
| `MARKET_DATA_RETENTION_TTL` | 24h |
| `MARKET_DATA_MOCK_WINDOW` | 60s |
| `FINNHUB_BASE_URL` | https://finnhub.io/api/v1 |
| `FINNHUB_API_KEY` | empty |
| `MARKET_DATA_CONNECT_TIMEOUT_MS` | 1000 |
| `MARKET_DATA_READ_TIMEOUT_MS` | 2000 |
| `MARKET_DATA_MAX_ATTEMPTS` | 2 |
| `MARKET_DATA_DEMO_CONTROLS_ENABLED` | false |

### 18.4 Dashboard scheduler

| 环境变量 | 默认 |
| --- | --- |
| `DASHBOARD_SNAPSHOT_SCHEDULING_ENABLED` | true |
| `DASHBOARD_SNAPSHOT_INTERVAL` | 15m |
| `DASHBOARD_SNAPSHOT_INITIAL_DELAY` | 15m |

### 18.5 `.env`

`.env.example` 是模板。Docker Compose 会自动读取仓库根目录 `.env`。

安全流程：

```bash
cp .env.example .env
chmod 600 .env
```

`.env` 必须保持 ignored，不得 commit。CI 不需要真实 Finnhub key。

---

## 19. 本地运行

### 19.1 Mock

```bash
./start.sh -d
```

检查：

```bash
curl http://localhost:8080/api/health
curl http://localhost:3000/api/health
```

### 19.2 Finnhub

```bash
export MARKET_DATA_PROVIDER=finnhub
export FINNHUB_API_KEY='<local secret>'
docker compose up -d --build
```

不要在命令历史、截图或日志中展开真实 key。

### 19.3 Swagger

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

---

## 20. Demo 环境

### 20.1 启动

```bash
bash scripts/demo-up.sh
bash scripts/demo-seed.sh
```

固定 project name：

```text
equity-demo
```

端点：

- UI：`http://localhost:3100`；
- Swagger：`http://localhost:8180/swagger-ui.html`。

### 20.2 Seed 幂等性

Seed 只通过 REST API 创建：

- 至少两个账户；
- 多 ticker；
- BUY/SELL；
- CANCELLED Trade；
- 正/负 unrealized P&L 场景。

重复运行会查询现有数据，不应无限制造重复记录。

### 20.3 清理

```bash
bash scripts/demo-down.sh
```

脚本验证 project name 和 volume label，只删除 `equity-demo` 的 container、network 和 volumes。

---

## 21. 测试策略

### 21.1 Backend fast tests

```bash
cd backend
./mvnw test
```

Surefire 包含：

- Domain unit tests；
- Application service unit tests；
- H2 快速 API integration；
- Problem Details contract；
- OpenAPI smoke；
- ArchUnit。

普通 `mvn test` 不应启动 Docker。

### 21.2 Backend integration

```bash
cd backend
./mvnw verify -Pintegration
```

Failsafe：

- 只运行 `*IT.java`；
- `failIfNoTests=true`；
- MySQL 8.4 Testcontainer；
- Redis Testcontainer；
- Flyway V1–V7；
- Hibernate validate；
- Trade/Position 并发和重启；
- Redis JSON/TTL；
- Finnhub-compatible stub integration；
- Snapshot persistence。

报告目录：

```text
backend/target/surefire-reports/
backend/target/failsafe-reports/
```

### 21.3 Frontend

```bash
cd frontend
npm ci
npm test -- --run
npm run lint
npm run build
```

覆盖：

- navigation；
- Account create/update/deactivate；
- BUY/SELL/Cancel/Delete/Amend；
- ticker search；
- CSV parse/hash/duplicate confirmation；
- Position；
- Market Data status；
- Dashboard/P&L/history；
- i18n；
- error/loading/empty states。

### 21.4 Playwright

视口：

- desktop：1440×900；
- mobile：390×844。

配置：

- workers=1；
- 每测试 120 秒；
- expect 15 秒；
- failure 保留 trace、screenshot、video；
- 不使用全页面截图作为主要断言。

运行：

```bash
cd frontend
npx playwright install --with-deps chromium
cd ..
CI=true GITHUB_RUN_ID=local GITHUB_RUN_ATTEMPT=1 \
  bash scripts/ci-e2e.sh
```

### 21.5 Compose smoke

```bash
CI=true GITHUB_RUN_ID=local GITHUB_RUN_ATTEMPT=1 \
  bash scripts/ci-smoke.sh
```

脚本验证：

- MySQL/Redis/backend/frontend/stub；
- health；
- Account 和 Trade；
- BUY/SELL/Cancel；
- Position；
- Market Data cache；
- stale fallback；
- Dashboard/P&L；
- backend restart persistence；
- Redis 与 MySQL 数据职责；
- project-specific cleanup。

---

## 22. GitHub Actions

Workflow：`.github/workflows/ci.yml`

触发：

- pull_request -> main；
- push -> main；
- workflow_dispatch。

全局：

- `contents: read`；
- concurrency；
- 同 PR/branch 新运行取消旧运行；
- 无 `continue-on-error`。

Jobs：

```text
backend ───┐
           ├─> compose-smoke ──┐
frontend ──┤                   ├─> quality-gate
           └─> e2e ────────────┘
```

`quality-gate` 使用 `if: always()`，但会显式要求所有上游结果等于 success。任何 failed/cancelled/skipped 都使其失败。

失败 artifacts：

- Surefire/Failsafe reports；
- Compose ps/logs；
- Playwright report；
- screenshot/video/trace；
- isolated Compose diagnostics。

注意：GitHub `pull_request` job 默认测试 PR 与最新 base 的临时 merge commit，而不是只测试 PR branch HEAD。若 `main` 在 PR 开发期间改变可访问文案或 API，E2E 可能只在 GitHub synthetic merge 中暴露问题。

---

## 23. 安全

### 23.1 Secret

严禁提交：

- `.env`；
- Finnhub key；
- token；
- private key；
- 含 header 的 debug dump；
- CI dummy token 之外的真实 credential。

Finnhub key：

- 只通过环境变量读取；
- 只通过 `X-Finnhub-Token` 发送；
- 不进入 query；
- 不进入 Provider status；
- 不进入 Problem Details；
- 不进入 README 示例的真实值。

### 23.2 API 安全边界

当前没有 authentication，任何能访问 Backend 的客户端都能操作所有 Account 和 Trade。因此：

- 仅适合本地、教学或受控网络；
- 不应直接暴露到公网；
- 生产化前必须增加身份认证、授权、CSRF/CORS 策略、rate limiting 和审计用户身份。

### 23.3 错误信息

日志只记录：

- ticker；
- failure category；
- exception class simple name；
- HTTP status category。

不记录：

- API key；
- request header；
- 原始 SQL；
- Provider response body；
- 用户敏感账户信息。

---

## 24. Observability

当前可用：

- `/api/health`；
- Docker healthcheck；
- Provider status 的 last success/failure；
- safe warning logs；
- CI artifacts。

当前缺少：

- Prometheus/Micrometer metrics export；
- cache hit ratio；
- Provider latency histogram；
- stale fallback counter；
- structured trace ID；
- distributed tracing；
- production alerting。

如果未来增加 metrics，建议至少：

```text
market_data_request_total{provider,result}
market_data_cache_hit_total{fresh,stale}
market_data_provider_latency_seconds
trade_booking_total{side,result}
trade_conflict_total{reason}
csv_import_total{result,duplicate}
dashboard_refresh_seconds
```

不得将 ticker/accountId 作为无限基数 metric label。

---

## 25. 故障排查

### 25.1 Backend unhealthy

```bash
docker compose ps
docker compose logs backend
docker compose logs db
curl -i http://localhost:8080/api/health
```

检查：

- MySQL 是否 healthy；
- DB_URL/用户名/密码；
- Flyway 是否成功；
- Hibernate validate 是否发现 migration 缺失；
- 端口冲突。

### 25.2 Finnhub 模式启动失败

典型原因：

```text
FINNHUB_API_KEY must be configured when MARKET_DATA_PROVIDER=finnhub
```

检查环境：

```bash
test -n "${FINNHUB_API_KEY:-}" && echo configured
```

不要 `echo "$FINNHUB_API_KEY"`。

### 25.3 Quote 总是 cached

检查：

- fresh TTL；
- Redis key 的 fetchedAt；
- Provider runtime status；
- demo outage；
- 是否使用普通 GET 而非 refresh。

```bash
docker compose exec redis redis-cli TTL market:quote:AAPL
docker compose exec redis redis-cli GET market:quote:AAPL
curl http://localhost:8080/api/market-data/provider/status
```

### 25.4 Stale fallback 不生效

前提：

1. Redis 中必须已经有 retained quote；
2. quote 已 stale 或请求 refresh；
3. Provider 当前失败；
4. Redis 本身必须可读；
5. key 未超过 retention TTL。

如果 Redis 本身宕机，系统无法读取 retained quote，因此 Provider 同时失败时会返回 503。

### 25.5 SELL 返回 409

检查：

- Account；
- ticker；
- executedAt；
- BOOKED Trade 时间序列；
- 是否存在 CANCELLED/DELETED/AMENDED BUY；
- backdated SELL 是否错误使用未来 BUY。

错误 `errors.quantity` 会显示执行时可用持仓。

### 25.6 CSV 重复提示不出现

检查：

- V7 migration 是否存在；
- registration API 是否先于 Trade API 调用；
- contentHash 是否 64 位小写 SHA-256；
- Account UUID、价格、时间是否真正等价；
- Browser 是否支持 Web Crypto；
- 是否清空了 MySQL registry。

Redis 清空不会影响 CSV duplicate detection，因为 Registry 在 MySQL。

### 25.7 PR E2E 本地通过、GitHub 失败

优先检查：

1. PR 是否落后 `origin/main`；
2. GitHub job 测试的 merge commit；
3. `gh run view --log-failed`；
4. Playwright artifact；
5. screenshot 的实际 accessible name；
6. Docker build cache 是否使本地使用旧 frontend image。

不要直接增加 timeout/retry掩盖稳定的 locator mismatch。

---

## 26. 开发规范

### 26.1 新后端功能

建议顺序：

1. Domain model/port；
2. Domain unit tests；
3. Application use case；
4. Infrastructure adapter；
5. API DTO/Controller；
6. Problem Details；
7. H2/API tests；
8. MySQL/Redis IT；
9. ArchUnit；
10. OpenAPI；
11. frontend；
12. Compose smoke/E2E。

### 26.2 Schema 修改

- 不修改旧 migration；
- 新增下一版本；
- 先 nullable/backfill，再 not null；
- 明确 UUID columnDefinition；
- 外键前先清理/回填数据；
- 增加真实 MySQL Testcontainers 断言；
- 更新 OpenAPI、README 和本文档。

### 26.3 时间和数值

- 所有业务时间使用注入 Clock；
- persistence 与 JDBC 使用 UTC；
- DATETIME(6)；
- ticker 使用 Locale.ROOT；
- 金额计算使用 BigDecimal/DECIMAL128；
- 禁止业务计算经过 double/float；
- API scale 最多 6；
- 前端只展示后端结果，不重新计算核心 P&L。

### 26.4 Git

- Feature branch；
- 小而清晰的 commit；
- 不提交 target/node_modules/dist/test-results；
- 不提交 `.env`；
- PR 必须等待 quality-gate；
- PR 长时间未合并时，验证与最新 main 的 synthetic merge。

---

## 27. 已知风险和技术债

### P0：生产化前必须处理

- 无 authentication/authorization；
- 默认开发密码不可用于生产；
- 未配置公网部署安全策略；
- 没有数据备份/恢复 runbook；
- 没有生产 secret manager；
- 前端金额使用 JavaScript number。

### P1：可靠性和可运维性

- Redis + Provider 同时故障时无本地 fallback；
- 无 cache hit/latency/stale metrics；
- 无请求级 trace/correlation ID；
- Dashboard historical Finnhub entitlement 不稳定；
- 没有大数据量性能基线。

### P2：业务扩展

- 无 realized P&L；
- 无 cash ledger；
- 无 settlement lifecycle；
- 无 WebSocket；
- 无 tax lots；
- 无 corporate actions。

---

## 28. 推荐扩展点

### 28.1 Realized P&L

应新增独立 domain，避免修改当前 weighted-average unrealized 逻辑。需要决定 weighted-average realized 还是 FIFO lots，并新增持久化/审计策略。

### 28.2 Redis observability

可以在 `MarketDataCache` 和 `MarketDataProvider` 外增加 decorator：

```text
ObservedMarketDataCache
ObservedMarketDataProvider
```

Application 不需要依赖 Micrometer。

### 28.3 第二行情 Provider

实现相同 ports：

- quote；
- instrument search；
- historical daily price。

通过配置选择，不修改 P&L/Trade Domain。

### 28.4 Authentication

需要在 Account 上增加 owner/user 外键，并在 Repository port 层强制 tenant filter，而不是只在 Controller 检查。

---

## 29. 发布检查清单

### Git

- [ ] 当前分支正确；
- [ ] 工作区干净；
- [ ] 与 origin/main 无意外分叉；
- [ ] PR 合并结果可构建；
- [ ] quality-gate SUCCESS。

### Backend

- [ ] `./mvnw test`；
- [ ] `./mvnw verify -Pintegration`；
- [ ] Failsafe reports 非空且无 skipped；
- [ ] Flyway V1–V7 success；
- [ ] Hibernate validate success。

### Frontend

- [ ] `npm ci`；
- [ ] `npm test -- --run`；
- [ ] `npm run lint`；
- [ ] `npm run build`；
- [ ] desktop/mobile E2E。

### Docker

- [ ] `docker compose config`；
- [ ] isolated smoke；
- [ ] restart persistence；
- [ ] Redis stale fallback；
- [ ] isolated volumes 清理；
- [ ] 默认开发 volumes 保留。

### Security

- [ ] 无 `.env` staged；
- [ ] 无 API key/token/private key；
- [ ] OpenAPI 无 secret；
- [ ] logs 无 header；
- [ ] artifacts 无敏感响应；
- [ ] Problem Details 无内部异常。

---

## 30. 术语表

| 术语 | 含义 |
| --- | --- |
| Account | 证券账户，不是系统登录用户 |
| Activity | Trade ledger 和相关操作 |
| BOOKED | 当前有效、参与 Position 的 Trade |
| CANCELLED | 被排除但保留审计的 Trade |
| Position | BOOKED Trade 的计算结果 |
| Cost Basis | 当前持仓的未实现成本基础 |
| Unrealized P&L | 当前市值减成本，不含已实现收益 |
| Fresh | Quote 的 fetchedAt 在 fresh TTL 内 |
| Cached | Quote 来自 Redis |
| Stale | Provider 失败后返回 retained Redis quote |
| Mock | 系统生成且明确标识的演示行情 |
| Live | 最新成功的 Finnhub quote；stale 时不能标为 Live |
| Snapshot | 某时刻总体 P&L totals 的持久化记录 |
| Historical valuation | 按执行时间和日收盘价重建的历史估值 |
| Problem Details | RFC 风格标准错误 JSON |
| Synthetic merge | GitHub 将 PR 与最新 base 临时合并后的测试 commit |

---

## 31. 关键源码索引

| 关注点 | 文件 |
| --- | --- |
| Trade domain | `backend/.../trade/domain/Trade.java` |
| Trade use cases | `backend/.../trade/application/TradeApplicationService.java` |
| Position algorithm | `backend/.../position/domain/PositionCalculator.java` |
| P&L algorithm | `backend/.../pnl/domain/PnlCalculator.java` |
| Market orchestration | `backend/.../marketdata/application/MarketDataApplicationService.java` |
| Redis cache | `backend/.../marketdata/infrastructure/redis/RedisMarketDataCache.java` |
| Finnhub adapter | `backend/.../marketdata/infrastructure/provider/FinnhubMarketDataProvider.java` |
| CSV registry | `backend/.../trade/application/TradeImportApplicationService.java` |
| Error contract | `backend/.../trade/api/TradeExceptionHandler.java` |
| Flyway | `backend/src/main/resources/db/migration/` |
| Frontend API | `frontend/src/api.ts` |
| CSV parser/hash | `frontend/src/trade/tradeCsv.ts` |
| CSV UI | `frontend/src/trade/TradeCsvImport.tsx` |
| i18n | `frontend/src/i18n.tsx` |
| E2E | `frontend/e2e/final-demo.spec.ts` |
| CI | `.github/workflows/ci.yml` |
| Compose | `compose.yaml` |

---

## 32. 结论

当前系统已经形成完整的 Equity Trade Booking Engine 闭环：

```text
Account
  -> verified BUY/SELL booking
  -> audit-preserved lifecycle
  -> chronological Position
  -> Redis-backed Mock/Finnhub quote
  -> unrealized P&L
  -> Dashboard/history
  -> browser UI
  -> automated quality gate
```

系统最重要的工程特征不是页面数量，而是：

- MySQL 是明确的业务权威存储；
- Trade 生命周期保留审计；
- 并发 SELL 使用数据库行锁防止超卖；
- Position 和 P&L 使用确定性时间顺序和 BigDecimal；
- Market Data 通过 port 隔离 Provider；
- Redis 只缓存行情且支持 retained stale fallback；
- 错误使用安全一致的 Problem Details；
- CSV 重复判断基于规范化表内容而不是文件名；
- Testcontainers、Compose smoke 和 Playwright 验证真实跨层路径；
- CI 使用最终 `quality-gate` 汇总所有质量检查。

生产化的主要差距是 authentication、secret/backup/observability、严格的前端 decimal 表达和更完整的运营能力，而不是核心 Trade Booking 流程。
