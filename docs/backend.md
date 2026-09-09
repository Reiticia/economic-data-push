# Market Event Analyzer

基于 Rust 的宏观财经事件研究工具，实现了 [项目设计](rust_macro_event_analyzer_design.md) 中建议的单进程后端 MVP：财经日历采集、SQLite 状态持久化、事件监听、跨市场报价、规则分析、REST API 与 WebSocket。

## 已实现

- Trading Economics 日历 Provider：HTML 解析被限制在 Provider 内，业务层只接收 owned domain model。
- `scheduled → watching → released → collecting_market_data → analyzing → completed` 状态机；状态落库并支持进程重启后继续。
- 每次日历采集都写入 observation，可追踪 Actual 发布和 Previous 修正。
- 相同国家、相同发布时间的指标自动归入 release group。
- Binance 加密货币、BiQuote 外汇/贵金属/DXY 与 Yahoo 股票指数/美债 Provider，业务层统一使用 `MarketSymbol`。
- 默认采集行情页的10个核心资产（两类股票指数、贵金属、美元/外汇、美债和加密货币）；可在配置中调整。
- 以 T-1m 为基准计算 T+1/5/15/30/60m 反应；收益率使用 bp，其他资产使用百分比。
- TOML 指标规则、Surprise (`Actual - Consensus`)、宏观信号、理论反应、实际反应和方向一致性分析。
- Axum REST API、WebSocket 实时事件、CORS、结构化日志和优雅退出。

## 启动

需要 Rust 1.85+：

```bash
cd backend
cargo run
```

服务默认监听 `0.0.0.0:8080`，首次启动会创建 `data/market.db` 并执行迁移。配置入口是 `config.toml`；也可以设置 `APP_CONFIG` 指向另一份配置。规则位于 `rules.toml`。

常用环境变量：

```bash
RUST_LOG=market_event_analyzer=debug,tower_http=info
APP_CONFIG=config.local.toml
```

## API

| 方法 | 地址 | 说明 |
|---|---|---|
| GET | `/health` | 存活检查 |
| GET | `/api/v1/events/upcoming?days=7` | 近期事件 |
| GET | `/api/v1/calendar?from=2026-09-07&to=2026-09-14&country=United%20States&minimum_importance=3` | 日历筛选 |
| GET | `/api/v1/events/{id}` | 事件与历次 observation |
| GET | `/api/v1/events/{id}/market` | 行情快照与各周期反应 |
| GET | `/api/v1/market/quotes?symbols=bitcoin,eur_usd,gold` | 批量当前行情；不传 `symbols` 时使用配置资产，单个源失败会列入 `unavailable` |
| GET | `/api/v1/events/{id}/analysis` | Surprise、信号、Expected/Observed 对比 |
| GET | `/api/v1/events/history?country=United%20States&category=inflation&limit=100&offset=0&from=2026-06-08&to=2026-09-07` | 按日期筛选并分页查询已入库历史事件；日期边界包含当天（UTC），最多93天 |
| GET | `/api/v1/history/backfill` | 最近补采任务状态、完成/部分/失败天数、事件数、报告数及最多20条按日错误；未运行时为 null |
| WS | `/api/v1/ws` | 发布、行情采集与分析完成事件 |

当前行情在服务端按品种缓存，默认新鲜缓存30秒、失败时最多复用5分钟内的最后成功报价并标记 `stale=true`。同一品种的并发请求会合并为一次上游请求；Yahoo 请求还会全局串行限速，收到429后遵循 `Retry-After` 冷却，避免客户端数量或一次批量请求放大为请求风暴。可在 `[market]` 中调整：

```toml
live_quote_cache_seconds = 30
live_quote_stale_seconds = 300
```

所有时间在服务端和数据库中统一为 UTC。宏观数字以 Decimal 的 JSON 字符串返回，避免浮点精度损失。

事件对象保留数据源英文名称 `event`，并额外返回可空的简体中文 `eventZhCn` 和繁体中文 `eventZhTw`：

```json
{
  "event": "CPI YoY",
  "eventZhCn": "消费者价格指数同比",
  "eventZhTw": "消費者價格指數同比"
}
```

### 大模型事件名翻译

翻译使用 OpenAI-compatible `POST /chat/completions` 接口。启用时只把尚未缓存的、去重后的英文事件名按批发送给模型；结果先写入 `event_name_translation`，再写回所有同名事件。服务重启、日历重复同步、历史与实时数据出现相同事件名时均直接复用数据库结果，不会二次消耗 token。模型或网络临时失败不会阻止英文事件入库，下一轮会重试尚未成功缓存的名称。

在 `config.local.toml` 中配置（不要把密钥写入 TOML）：

```toml
[translation]
enabled = true
base_url = "https://api.openai.com/v1"
model = "gpt-5-mini"
api_key_env = "OPENAI_API_KEY"
batch_size = 20
backfill_on_startup = true
```

启动前设置密钥：

```powershell
$env:OPENAI_API_KEY = "你的 API Key"
$env:APP_CONFIG = "config.local.toml"
cargo run
```

`backfill_on_startup=true` 会对数据库里已有但未翻译的不同事件名做一次补翻译；已缓存名称不会再次请求模型。WebSocket 的 `economic_event_released` 消息同样返回 `event`、`eventZhCn` 和 `eventZhTw`。

## 过去三个月：日历与事件窗口涨跌补采

补采是**管理员 CLI 命令**，不会在普通服务启动时自动运行，也没有未鉴权的公开写入接口。需要具有历史日历权限的 Trading Economics API 凭据；公共 HTML 页面可能忽略日期参数，禁止作为历史回退源。缺少凭据时立即退出，不写入虚假历史任务或数据。

```powershell
cd backend
# 凭据仅设置在本机环境，不要提交仓库或粘贴到日志。
$env:TE_API_KEY = "你的有效凭据"
cargo run -- --backfill
# 或指定需要补采/重试的 UTC 日期区间（最多93天，结束日必须早于今天）
cargo run -- --backfill 2026-06-08 2026-09-07
```

Bash 对应 `export TE_API_KEY='你的有效凭据'`。默认范围为 UTC 今天往前**三个日历月**至昨天，不是固定90天。配置使用同一个数据库，导入后启动普通后端，App 历史页点击刷新即可查看；支持加载更早的事件，不再仅显示美国最近100条。

### 数据真实性与覆盖范围

- 日历按天调用官方 API，验证返回事件全部落在请求的 UTC 日期区间，达到1000条响应上限时拒绝可能被截断的数据。保留全天/模糊时间事件，但 `timeExact=false` 的事件不计算日内反应。
- `Forecast` 映射市场共识，`TEForecast` 映射数据源自身预测。历史数值可能已修订，不能当作公布时刻可交易的原始数据版本。
- 每天每个配置资产只获取一次行情窗口（含前10分钟、次日61分钟），真实K线与实时快照分表缓存；同一发布组的事件复用缓存。Binance 与 BiQuote 都会按上游单次1000根限制分段请求。
- BiQuote 提供外汇、黄金、白银和 DXY 的实时中间价及分钟 K 线；其当前行情会保留 `marketState` 与 `stale`，事件窗口采集则拒绝陈旧报价。其历史源出现实际缺口时仍保留缺失，不插值。
- 免费 Yahoo 默认保守使用最近7天的1分钟线、约59天内的5分钟线；更老的窗口标记 `retention_limit`，**不以小时线/日线伪装分钟反应**。已有真实分钟线缓存可在源保留期过后继续使用。完整三个月的股票指数和美债分钟行情，需要另接具有足够回溯权限的数据源。
- Binance 的历史分钟线受当地服务可用性限制。HTTP 401/403/410/451 不盲目重试；429、5xx、超时等最多尝试3次并退避。不绕过服务访问限制。
- K线时间戳是开盘时间，计算时加上周期得到收盘可知时间。基准必须严格早于公布时间，且距离公布不超过一个K线周期；观察价格不晚于 T+1/5/15/30/60m，最多向前容忍59秒。5分钟线不计算1分钟反应。
- 美债收益率变化单位是基点，其他资产为百分比。不存在基准、市场休市、精度不足等情况保留缺失，不填0、不插值、不使用当前报价。BiQuote 黄金使用 `XAUUSD`、美元指数使用 `DXY`；Yahoo 纳指为 `^NDX`、10年美债为 `^TNX`，这些仍不是可直接交易的统一现货组合。
- 历史事件保持 `historical` 状态，不进入实时监听器，不补发旧事件通知；已实时采集并生成报告的事件及其数值不会被历史版本覆盖，公共网页刷新也不会清空已归档的官方 API 数值。仍由实时监听器处理、或公布后60分钟尚未结束的事件暂缓处理，将该日标为 partial，稍后重跑。

### 结果、重试与审计

命令打印任务摘要：`complete` 仅表示请求的全部事件窗口已覆盖；有缺失则为 `partial` 并以非零退出码提示。日历没有事件的正常日期可为 complete。无法访问账户会停止任务，而不是继续请求所有日期。

同一区间重跑会跳过完整日期，重试失败/部分日期；事件与行情按唯一键幂等入库。Ctrl-C 后可立即重跑；异常退出时同一区间的运行租约15分钟后过期。事件观察记录保留每次重新获取的版本。更换资产配置后，如需重做已完成日期，应使用新的数据库或在备份后由管理员清理对应任务的 `backfill_day` 检查点。

`/api/v1/events/{id}/analysis` 新增可选 `historical` 字段，包括 `fetchedAt`、`revisedDataPossible` 和逐资产 `coverage`。覆盖记录含来源、K线周期、可用观察窗口、实际基准/样本时间、缺失原因。App 分析页会展示三语覆盖说明；历史报告不展示使用实时快照驱动的时间轴。原 `/market` 接口保留快照与已计算涨跌：历史补采不制造实时快照。

## 验证

```bash
cd backend
cargo fmt -- --check
cargo check
cargo test
```

测试覆盖 HTML/API 解析、日期范围验证、凭据脱敏、Binance 分页、BiQuote 行情新鲜度与未收盘K线过滤、Surprise、历史K线收盘时点/禁止前视/缺失窗口、收益率基点、按日缓存与断点重试、保留实时报告，以及 SQLite upsert/历史分页/状态与修正记录。所有合成测试数据仅写入内存数据库，不混入真实数据。

## 部署

```bash
cd backend
docker build -t market-event-analyzer .
docker run --rm -p 8080:8080 -v ./data:/app/data market-event-analyzer
```

生产使用前请确认 Trading Economics 的使用条款、目标地区访问情况和页面 DOM。页面变化只需调整 `src/calendar/trading_economics.rs`。

Android 客户端没有混入此后端工程；REST/WebSocket 契约已经可以直接供 Kotlin + Compose 客户端接入。
