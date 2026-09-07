# Market Event Analyzer

基于 Rust 的宏观财经事件研究工具，实现了 [项目设计](rust_macro_event_analyzer_design.md) 中建议的单进程后端 MVP：财经日历采集、SQLite 状态持久化、事件监听、跨市场报价、规则分析、REST API 与 WebSocket。

## 已实现

- Trading Economics 日历 Provider：HTML 解析被限制在 Provider 内，业务层只接收 owned domain model。
- `scheduled → watching → released → collecting_market_data → analyzing → completed` 状态机；状态落库并支持进程重启后继续。
- 每次日历采集都写入 observation，可追踪 Actual 发布和 Previous 修正。
- 相同国家、相同发布时间的指标自动归入 release group。
- Yahoo 市场 Provider 与 Binance 加密货币 Provider，业务层统一使用 `MarketSymbol`。
- 默认采集 Gold、NASDAQ 100、DXY、US 10Y 和 Bitcoin；可在配置中扩充。
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
| GET | `/api/v1/events/{id}/analysis` | Surprise、信号、Expected/Observed 对比 |
| GET | `/api/v1/events/history?country=United%20States&category=inflation&limit=100` | 历史事件 |
| WS | `/api/v1/ws` | 发布、行情采集与分析完成事件 |

所有时间在服务端和数据库中统一为 UTC。宏观数字以 Decimal 的 JSON 字符串返回，避免浮点精度损失。

## 验证

```bash
cd backend
cargo fmt -- --check
cargo check
cargo test
```

测试覆盖 HTML 解析和数值标准化、Surprise、指标反向规则、市场周期反应，以及 SQLite upsert/状态保留/修正记录。

## 部署

```bash
cd backend
docker build -t market-event-analyzer .
docker run --rm -p 8080:8080 -v ./data:/app/data market-event-analyzer
```

生产使用前请确认 Trading Economics 的使用条款、目标地区访问情况和页面 DOM。页面变化只需调整 `src/calendar/trading_economics.rs`。

Android 客户端没有混入此后端工程；REST/WebSocket 契约已经可以直接供 Kotlin + Compose 客户端接入。
