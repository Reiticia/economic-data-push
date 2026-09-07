# 宏观财经事件分析工具设计方案

> 目标：构建一个用于个人学习和研究的财经事件分析工具。  
> 后端使用 Rust，Android 端负责展示、通知和交互。  
> 数据来源以 Trading Economics 财经日历网页为主，并结合公开市场行情数据源，对宏观事件公布后的市场反应进行分析。

---

## 1. 项目目标

系统主要完成以下任务：

1. 自动获取未来 7 天的重要财经事件。
2. 获取事件的：
   - 发布时间
   - 国家 / 地区
   - 事件名称
   - Importance
   - Previous
   - Consensus
   - Forecast
   - Actual
3. 在重要经济数据公布前自动进入监听状态。
4. 数据公布后自动检测 Actual。
5. 对比 Actual 与 Consensus，计算 Surprise。
6. 同时采集：
   - 黄金
   - 白银
   - 美股
   - 美债收益率
   - 美元 / 外汇
   - 原油
   - BTC / ETH
7. 分析事件公布后：
   - 1 分钟
   - 5 分钟
   - 15 分钟
   - 30 分钟
   - 60 分钟
   的市场走势。
8. 建立历史事件数据库。
9. 最终形成类似：
   - “数据是否超预期”
   - “理论影响是什么”
   - “市场实际如何反应”
   - “实际走势是否符合理论”
   - “历史类似事件通常如何表现”
   的分析结果。

---

# 2. 整体架构

```text
                    Internet
                       │
        ┌──────────────┼───────────────┐
        │              │               │
        ▼              ▼               ▼
 Trading Economics   Yahoo/其他       Binance
 财经日历 HTML        市场行情          Crypto
        │              │               │
        └──────────────┼───────────────┘
                       ▼
              ┌─────────────────┐
              │   Rust Backend  │
              │                 │
              │  Collector      │
              │  Scheduler      │
              │  Analyzer       │
              │  Rule Engine    │
              │  REST API       │
              │  WebSocket      │
              └───────┬─────────┘
                      │
                 SQLite DB
                      │
                      ▼
               Android App
              Kotlin + Compose
```

设计原则：

- Android 只负责展示和通知。
- 数据抓取放在 Rust 后端。
- Trading Economics 网页解析逻辑与业务逻辑隔离。
- 行情数据源通过 Provider 抽象。
- 使用 SQLite 保存历史数据。
- 单进程完成采集、调度、分析和 API 服务。
- 第一阶段不使用微服务、Kafka、Redis、PostgreSQL。

---

# 3. Rust 技术栈

推荐：

```text
Rust
├── Tokio
├── Axum
├── Reqwest
├── Scraper
├── SQLx
├── SQLite
├── Serde
├── rust_decimal
├── tracing
└── TOML
```

模块对应：

| 功能 | 技术 |
|---|---|
| Async Runtime | Tokio |
| REST API | Axum |
| WebSocket | Axum |
| HTTP Client | Reqwest |
| HTML Parser | scraper |
| JSON | Serde |
| DB | SQLite |
| DB Access | SQLx |
| Decimal | rust_decimal |
| 日志 | tracing |
| 配置 | TOML |
| 内部消息 | Tokio broadcast / mpsc |

---

# 4. 项目目录结构

```text
market-event-analyzer/
│
├── Cargo.toml
├── Dockerfile
├── config.toml
│
├── migrations/
│   ├── 001_events.sql
│   ├── 002_observations.sql
│   ├── 003_market_data.sql
│   └── 004_analysis.sql
│
└── src/
    ├── main.rs
    │
    ├── api/
    │   ├── mod.rs
    │   ├── calendar.rs
    │   ├── event.rs
    │   ├── market.rs
    │   └── websocket.rs
    │
    ├── calendar/
    │   ├── mod.rs
    │   ├── provider.rs
    │   └── trading_economics.rs
    │
    ├── market/
    │   ├── mod.rs
    │   ├── provider.rs
    │   ├── yahoo.rs
    │   └── binance.rs
    │
    ├── scheduler/
    │   ├── mod.rs
    │   ├── calendar_sync.rs
    │   └── event_watcher.rs
    │
    ├── analysis/
    │   ├── mod.rs
    │   ├── surprise.rs
    │   ├── rule_engine.rs
    │   ├── market_reaction.rs
    │   └── report.rs
    │
    ├── model/
    │   ├── event.rs
    │   ├── observation.rs
    │   ├── market.rs
    │   └── analysis.rs
    │
    ├── repository/
    │   ├── event.rs
    │   ├── market.rs
    │   └── analysis.rs
    │
    ├── config.rs
    └── error.rs
```

第一版建议保持单 Cargo 项目，不拆 Workspace。

---

# 5. 财经日历数据源

主要来源：

```text
https://tradingeconomics.com/calendar
```

流程：

```text
Reqwest
   ↓
Trading Economics HTML
   ↓
scraper
   ↓
TradingEconomicsEvent
   ↓
标准化
   ↓
EconomicEvent
   ↓
SQLite
```

---

# 6. Calendar Provider 抽象

建议定义统一接口：

```rust
#[async_trait]
pub trait CalendarProvider {
    async fn fetch_events(
        &self,
        start: DateTime<Utc>,
        end: DateTime<Utc>,
    ) -> Result<Vec<EconomicEvent>, AppError>;
}
```

实现：

```rust
pub struct TradingEconomicsProvider {
    client: reqwest::Client,
}
```

未来可以增加：

```text
TradingEconomicsProvider
ForexFactoryProvider
BlsProvider
FedProvider
EiaProvider
```

核心业务层不关心数据来自哪个网站。

---

# 7. Trading Economics HTML 解析原则

不要让 HTML DOM 结构进入业务层。

正确流程：

```text
HTML
 ↓
TradingEconomicsParser
 ↓
Provider Raw Model
 ↓
EconomicEvent
 ↓
Domain Layer
```

不要：

```text
HTML DOM
 ↓
Service
 ↓
Controller
```

Trading Economics 改网页时，只修改：

```text
calendar/trading_economics.rs
```

---

# 8. scraper 与 Tokio 的注意事项

`scraper::Html` 不建议长期保存到 AppState。

推荐：

```rust
let html = client
    .get(url)
    .send()
    .await?
    .text()
    .await?;

let events = parse_calendar(&html);

// 后续只使用 owned EconomicEvent
repository.save_events(events).await?;
```

原则：

```text
await HTTP
  ↓
String
  ↓
同步解析 Html
  ↓
Vec<EconomicEvent>
  ↓
释放 Html
  ↓
继续 await
```

---

# 9. Reqwest Client 复用

不要：

```rust
reqwest::Client::new()
```

每请求一次创建一次。

建议：

```rust
pub struct AppState {
    pub http: reqwest::Client,
    pub db: SqlitePool,
}
```

全局复用 HTTP Client，利用连接池和 Keep-Alive。

---

# 10. 财经事件数据模型

建议：

```rust
pub struct EconomicEvent {
    pub id: i64,

    pub provider: String,
    pub provider_id: Option<String>,

    pub country: String,
    pub currency: Option<String>,

    pub category: String,
    pub event: String,

    pub event_time: DateTime<Utc>,

    pub importance: u8,

    pub actual: Option<Decimal>,
    pub previous: Option<Decimal>,
    pub consensus: Option<Decimal>,
    pub forecast: Option<Decimal>,

    pub unit: Option<String>,

    pub status: EventStatus,
}
```

---

# 11. 不使用 f64 保存宏观数据

建议使用：

```rust
rust_decimal::Decimal
```

例如：

```rust
pub actual: Option<Decimal>,
pub previous: Option<Decimal>,
pub consensus: Option<Decimal>,
```

适合：

- CPI
- GDP
- 利率
- 非农
- 失业率
- 百分比数据
- 金额

行情 tick 数据是否使用 Decimal 可以根据性能需求决定。

---

# 12. Economic Event 状态机

建议：

```rust
pub enum EventStatus {
    Scheduled,
    Watching,
    Released,
    CollectingMarketData,
    Analyzing,
    Completed,
    Timeout,
}
```

状态流程：

```text
SCHEDULED
    │
    ▼
WATCHING
    │
    ▼
RELEASED
    │
    ▼
COLLECTING_MARKET_DATA
    │
    ▼
ANALYZING
    │
    ▼
COMPLETED
```

异常：

```text
WATCHING
   ↓
TIMEOUT
```

所有状态必须落 SQLite。

这样服务重启后能够恢复。

---

# 13. Event Observation

不要只保存事件最终结果。

建议增加：

```text
event_observation
```

记录每次从 Trading Economics 看到的数据。

示例：

```text
20:29:30

Actual      NULL
Previous    2.8
Consensus   2.9
```

然后：

```text
20:30:18

Actual      3.2
Previous    2.7
Consensus   2.9
```

可以检测：

```text
Actual 发布
+
Previous 2.8 → 2.7
```

即历史数据修正。

建议结构：

```text
event_observation

id
event_id
observed_at

actual
previous
consensus
forecast
```

---

# 14. 财经日历普通同步

建议每天同步未来 7 天：

```text
06:00
18:00
```

流程：

```text
Scheduler
   ↓
TradingEconomicsProvider
   ↓
未来7天 Event
   ↓
Upsert
   ↓
SQLite
```

避免频繁请求整个网页。

---

# 15. 重大事件监听

假设：

```text
20:30 US CPI
Importance = 3
```

可以：

```text
20:25
进入 WATCHING
```

并开始行情采集。

财经事件刷新：

```text
20:29:30
20:30:00
20:30:15
20:30:30
20:30:45
20:31:00
...
```

检测：

```rust
if event.actual.is_some() {
    stop_watch();
    start_market_analysis();
}
```

建议最低 15 秒左右刷新一次，而不是 1 秒轮询。

---

# 16. Scheduler 设计

不建议给未来每个事件 spawn 一个长期 sleep task。

不推荐：

```rust
for event in events {
    tokio::spawn(async move {
        sleep_until(event.time).await;
    });
}
```

更推荐：

```text
EventWatcher
     ↓
周期扫描 SQLite
     ↓
查询：
event_time 接近当前时间
AND
status = SCHEDULED
     ↓
切换 WATCHING
```

优势：

- 重启可恢复
- 状态清晰
- 事件数量多也容易管理
- 未来可增加优先级

---

# 17. Tokio 后台任务

第一版可使用：

```rust
tokio::spawn(calendar_sync_loop(...));
tokio::spawn(event_watch_loop(...));
tokio::spawn(market_collect_loop(...));
```

无需 Quartz / RabbitMQ / Kafka。

---

# 18. 行情 Provider

定义：

```rust
#[async_trait]
pub trait MarketDataProvider {
    async fn quote(
        &self,
        symbol: &MarketSymbol,
    ) -> Result<Quote, AppError>;

    async fn candles(
        &self,
        symbol: &MarketSymbol,
        start: DateTime<Utc>,
        end: DateTime<Utc>,
        interval: Interval,
    ) -> Result<Vec<Candle>, AppError>;
}
```

可能实现：

```text
YahooProvider
BinanceProvider
```

以后可以替换：

```text
StooqProvider
TwelveDataProvider
InteractiveBrokersProvider
```

---

# 19. 市场 Symbol 抽象

不要让业务代码到处出现：

```text
GC=F
BTCUSDT
EURUSD=X
^TNX
```

建议：

```rust
pub enum MarketSymbol {
    Gold,
    Silver,

    Sp500,
    Nasdaq100,

    Us2Y,
    Us10Y,

    Dxy,

    EurUsd,
    GbpUsd,
    UsdJpy,

    Wti,
    Brent,

    Bitcoin,
    Ethereum,
}
```

Provider 自己负责映射。

例如：

```text
MarketSymbol::Gold

Yahoo:
GC=F

其他 Provider:
XAUUSD
```

---

# 20. 默认观察市场

## 贵金属

```text
Gold
Silver
```

## 美股

```text
S&P 500
NASDAQ
Dow Jones
```

## 美债

```text
US 2Y Yield
US 10Y Yield
```

其中宏观事件分析尤其建议观察：

```text
US 2Y
```

因为它通常对 Fed 利率预期更加敏感。

## 外汇

```text
DXY
EUR/USD
GBP/USD
USD/JPY
AUD/USD
```

## 能源

```text
WTI
Brent
Natural Gas
```

## Crypto

```text
BTC
ETH
```

---

# 21. 事件前行情基准

不要等经济数据公布后才开始采集。

例如：

```text
CPI = 20:30
```

应该：

```text
20:25
```

开始记录。

建议保存：

```text
T-5m
T-1m
T
T+1m
T+5m
T+15m
T+30m
T+60m
```

---

# 22. Market Snapshot

结构例如：

```text
event_market_snapshot

id
event_id

symbol
timestamp

price
open
high
low
close
volume
```

或者第一版只保存 Close / Price。

---

# 23. 市场反应计算

例如：

```text
Gold

T-1m = 2352
T+5m = 2330
```

计算：

```text
Change =
(2330 - 2352) / 2352

≈ -0.94%
```

输出：

```json
{
  "symbol": "GOLD",
  "change1m": "-0.31",
  "change5m": "-0.94",
  "change15m": "-1.10",
  "change60m": "-0.72"
}
```

---

# 24. Surprise Calculator

宏观事件的核心不是：

```text
Actual - Previous
```

而是：

```text
Actual - Consensus
```

例如：

```text
CPI

Actual       3.2
Consensus    2.9
Previous     2.8
```

则：

```text
Raw Surprise = +0.3
```

后续还可以标准化：

```text
Normalized Surprise
```

例如使用：

- 历史标准差
- 历史 Surprise 分布
- 指标自身量纲

---

# 25. 指标方向规则

不同指标的高低代表不同宏观意义。

## CPI

```text
Actual > Consensus
→ Inflation Stronger
→ Hawkish
```

## 非农

```text
Actual > Consensus
→ Employment Stronger
→ 通常 Hawkish
```

## 失业率

```text
Actual > Consensus
→ Employment Weaker
→ 通常 Dovish
```

## Initial Jobless Claims

```text
Actual > Consensus
→ Employment Weaker
```

## 原油库存

```text
Actual > Consensus
→ Inventory Higher
→ 通常 Bearish Oil
```

因此绝对不能直接：

```text
actual > consensus = bullish
```

---

# 26. Rule Engine

第一版推荐 TOML。

例如：

```toml
[[indicator]]
name = "Inflation Rate YoY"
category = "inflation"

higher_than_expected = "hawkish"
lower_than_expected = "dovish"

[[indicator]]
name = "Unemployment Rate"

higher_than_expected = "dovish"
lower_than_expected = "hawkish"

[[indicator]]
name = "Crude Oil Stocks Change"

higher_than_expected = "bearish_oil"
lower_than_expected = "bullish_oil"
```

加载为：

```rust
Arc<RuleEngine>
```

---

# 27. Macro Signal

建议：

```rust
pub enum MacroSignal {
    StrongHawkish,
    Hawkish,
    Neutral,
    Dovish,
    StrongDovish,

    BullishOil,
    BearishOil,
}
```

---

# 28. Expected Reaction

例如：

```text
CPI 高于预期
    ↓
Strong Hawkish
    ↓
Expected:

USD       ↑
US2Y      ↑
US10Y     ↑
Gold      ↓
NASDAQ    ↓
BTC       ↓ / Risk Sensitive
```

这里表示理论预期，而不是保证走势。

---

# 29. Observed Reaction

实际行情可能：

```text
DXY      +0.53%
US10Y    +8bp
NASDAQ   +0.20%
Gold     -1.10%
```

系统可以比较：

```text
Gold:
符合理论方向

DXY:
符合理论方向

NASDAQ:
与理论方向相反
```

---

# 30. Expected 与 Observed 必须分离

推荐模型：

```text
ExpectedReaction
ObservedReaction
```

不要直接生成：

```text
CPI 高于预期，所以黄金一定下跌
```

更合理：

```text
理论倾向：
Gold ↓

实际：
Gold -1.1%

结果：
符合典型 Hawkish 反应
```

---

# 31. 同时发布的事件需要 Group

例如美国 CPI：

```text
20:30

CPI YoY
CPI MoM
Core CPI YoY
Core CPI MoM
```

不能完全单独解释。

建议：

```text
ReleaseGroup
```

示例：

```text
US_CPI_2026_09
```

包含：

```text
CPI YoY
CPI MoM
Core CPI YoY
Core CPI MoM
```

最后得到：

```text
Inflation Signal:
Strong Hawkish

Score:
76 / 100
```

---

# 32. Internal Event Bus

Tokio 非常适合内部事件驱动。

例如：

```rust
enum AppEvent {
    EconomicEventReleased {
        event_id: i64,
    },

    MarketDataCollected {
        event_id: i64,
    },

    AnalysisCompleted {
        event_id: i64,
    },
}
```

流程：

```text
TradingEconomics Watcher
        │
        ▼
EconomicEventReleased
        │
        ├───────────────┐
        ▼               ▼
 MarketCollector      WebSocket
        │
        ▼
MarketDataCollected
        │
        ▼
AnalysisEngine
        │
        ▼
AnalysisCompleted
        │
        ├───────────────┐
        ▼               ▼
      DB              Android
```

可以使用：

```text
tokio::sync::broadcast
tokio::sync::mpsc
```

---

# 33. AppState

例如：

```rust
pub struct AppState {
    pub db: SqlitePool,

    pub http: reqwest::Client,

    pub calendar_service: Arc<CalendarService>,

    pub market_service: Arc<MarketService>,

    pub analysis_service: Arc<AnalysisService>,

    pub event_bus: broadcast::Sender<AppEvent>,
}
```

---

# 34. REST API

## 获取近期事件

```http
GET /api/v1/events/upcoming
```

## 获取财经日历

```http
GET /api/v1/calendar?from=2026-09-07&to=2026-09-14
```

## 获取事件详情

```http
GET /api/v1/events/{id}
```

## 获取分析

```http
GET /api/v1/events/{id}/analysis
```

## 获取某事件的市场行情

```http
GET /api/v1/events/{id}/market
```

## 获取某类指标历史

```http
GET /api/v1/events/history?country=US&category=cpi
```

---

# 35. WebSocket

建议：

```text
WS /api/v1/ws
```

事件：

```json
{
  "type": "economic_event_released",
  "eventId": 123,
  "event": "US CPI YoY",
  "actual": "3.2",
  "consensus": "2.9"
}
```

分析完成：

```json
{
  "type": "analysis_completed",
  "eventId": 123
}
```

Android 可以及时刷新。

---

# 36. SQLite 表建议

至少：

```text
economic_event
event_observation
release_group

market_snapshot
market_reaction

analysis_report
```

---

# 37. Android App

推荐：

```text
Kotlin
Jetpack Compose
Retrofit
OkHttp
Room
WebSocket
WorkManager
```

页面：

```text
Home
Calendar
Event Detail
Analysis
History
Settings
```

---

# 38. Home 页面

示例：

```text
Today

🔴 20:30 US CPI

Previous     2.7%
Consensus    2.8%
Actual       --

🟠 22:00 ISM Services PMI

Previous     51.2
Consensus    51.5
Actual       --
```

---

# 39. Event Detail

示例：

```text
US CPI

Time:
20:30

Actual:
3.2%

Consensus:
2.9%

Previous:
2.8%

Surprise:
+0.3%

Macro Signal:
Strong Hawkish
```

市场反应：

```text
Gold       -0.95%
NASDAQ     -0.62%
DXY        +0.55%
US10Y      +9bp
BTC        -1.42%
```

---

# 40. History

例如 CPI：

| Date | Surprise | Gold 5m | Nasdaq 5m | DXY 5m |
|---|---:|---:|---:|---:|
| Aug | +0.2 | -0.8% | -0.4% | +0.3% |
| Jul | -0.1 | +0.6% | +0.7% | -0.2% |
| Jun | +0.3 | -1.1% | -0.9% | +0.5% |

未来可以统计：

```text
过去 24 次美国 CPI

当 CPI Surprise > +0.2%

Gold:
15分钟平均 -0.63%

DXY:
15分钟平均 +0.41%

US10Y:
平均 +7.4bp
```

---

# 41. MVP 开发顺序

## Phase 1：财经日历

实现：

```text
Trading Economics
        ↓
Reqwest
        ↓
scraper
        ↓
EconomicEvent
        ↓
SQLite
        ↓
REST API
```

功能：

- 今天
- 明天
- 未来 7 天
- Importance
- Previous
- Consensus
- Forecast
- Actual
- 国家筛选

---

## Phase 2：自动监听

实现：

```text
Event Scheduler
       ↓
WATCHING
       ↓
Actual 出现
       ↓
RELEASED
       ↓
WebSocket
       ↓
Android 通知
```

通知例如：

```text
美国 CPI 已公布

Actual: 3.2%
Consensus: 2.9%

高于预期 0.3%
```

---

## Phase 3：市场行情

加入：

```text
Gold
NASDAQ
DXY
US2Y
US10Y
BTC
```

分析：

```text
1m
5m
15m
30m
60m
```

---

## Phase 4：规则引擎

实现：

```text
Actual
  ↓
Consensus
  ↓
Surprise
  ↓
Indicator Rule
  ↓
Macro Signal
  ↓
Expected Reaction
  ↓
Observed Reaction
```

---

## Phase 5：历史统计

例如：

```text
US CPI Surprise > 0.2%

历史 Gold 5m 平均
历史 DXY 5m 平均
历史 US2Y 5m 平均
```

逐渐形成自己的：

```text
Macro Event Database
```

---

# 42. AI / LLM 的位置

第一版不要依赖 AI 做核心判断。

推荐：

```text
Rule Engine
     +
Market Reaction
     +
Historical Similar Events
     ↓
Structured Analysis
     ↓
LLM
     ↓
自然语言总结
```

LLM 最好作为：

```text
解释层
```

而不是：

```text
核心计算层
```

---

# 43. 部署方案

Rust 非常适合直接部署。

服务器文件：

```text
/opt/market-analyzer/
├── market-analyzer
├── config.toml
└── data/
    └── market.db
```

运行：

```bash
./market-analyzer
```

---

# 44. systemd

推荐长期运行使用 systemd。

例如：

```ini
[Unit]
Description=Market Event Analyzer
After=network.target

[Service]
Type=simple
User=market
WorkingDirectory=/opt/market-analyzer
ExecStart=/opt/market-analyzer/market-analyzer
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

---

# 45. Docker

以后也可以：

```bash
docker run -d \
  --name market-analyzer \
  -p 8080:8080 \
  -v ./data:/app/data \
  market-analyzer
```

容器：

```text
/app/market-analyzer
/app/data/market.db
```

---

# 46. HTTP TLS 依赖

为了降低 Linux 部署依赖，推荐 Reqwest 使用 rustls。

目标：

```text
尽量避免依赖系统 OpenSSL
```

这样构建 Docker 镜像或者复制二进制更加方便。

---

# 47. 第一版不需要的东西

第一版暂时不要：

```text
Kafka
RabbitMQ
Redis
PostgreSQL
Kubernetes
微服务
复杂消息队列
分布式 Scheduler
```

推荐：

```text
Rust
+
Tokio
+
Axum
+
SQLite
```

一个进程即可。

---

# 48. 核心设计原则

## Provider 与业务层隔离

```text
Provider
   ↓
Standard Model
   ↓
Domain Service
   ↓
Analyzer
```

---

## Trading Economics DOM 不进入业务层

```text
HTML
 ↓
TradingEconomicsParser
 ↓
EconomicEvent
```

网页结构变化只修改 Provider。

---

## 第三方 Symbol 不进入业务层

不要：

```text
GC=F
BTCUSDT
^TNX
```

直接出现在分析器。

统一使用：

```text
MarketSymbol::Gold
MarketSymbol::Bitcoin
MarketSymbol::Us10Y
```

---

## Expected 与 Observed 分离

必须区分：

```text
理论应该怎么走
```

与：

```text
市场实际上怎么走
```

这会成为整个工具最有研究价值的地方。

---

# 49. 最终目标

系统最终不是单纯：

> 今天有哪些财经数据？

而是回答：

> 这个数据比市场预期高还是低？

> Surprise 有多大？

> 对宏观政策预期意味着什么？

> 理论上哪些资产应该上涨或下跌？

> 实际黄金、美债、美元、美股、原油、BTC 怎么走？

> 为什么走势符合 / 不符合理论？

> 历史上类似 Surprise 出现后，市场平均如何反应？

最终形成：

```text
Economic Calendar
       +
Market Reaction
       +
Historical Statistics
       +
Macro Rule Engine
       =
Macro Event Research Tool
```

---

# 50. 推荐 MVP 最小版本

如果只做第一版，建议范围控制在：

```text
Trading Economics 财经日历
+
US High Importance Events
+
SQLite
+
Axum REST API
+
Android Calendar 页面
```

重点事件：

```text
CPI
Core CPI
Nonfarm Payrolls
Unemployment Rate
Average Hourly Earnings
PCE
Core PCE
GDP
Initial Jobless Claims
ISM PMI
FOMC Rate Decision
EIA Crude Oil Inventories
```

行情先只接：

```text
Gold
NASDAQ
DXY
US10Y
BTC
```

等整个链路跑通后，再逐步扩展。

---

## 推荐最终技术选型

| 模块 | 技术 |
|---|---|
| Language | Rust |
| Runtime | Tokio |
| HTTP Server | Axum |
| HTTP Client | Reqwest |
| HTML Parser | scraper |
| Serialization | Serde |
| Database | SQLite |
| ORM / DB Access | SQLx |
| Decimal | rust_decimal |
| Logging | tracing |
| Config | TOML |
| Internal Events | Tokio broadcast / mpsc |
| Client API | REST + WebSocket |
| Android | Kotlin + Jetpack Compose |
| Deployment | Linux Binary / systemd |
| Alternative Deployment | Docker |

---

**建议开发方向：先完成 Rust 后端的 Trading Economics 抓取 + SQLite + Axum API，再进入 Android 页面开发。**
