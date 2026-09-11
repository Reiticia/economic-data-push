# 客户端直连架构

## 原则

- 不运行项目自建服务端，不在 APK 中嵌入部署地址或共享密钥。
- 日历、行情、K 线、规则分析和缓存均由 Android 客户端完成。
- 翻译是可选增强：没有用户 Key 时保持英文原文，核心数据仍可使用。
- 任何第三方失败都不得用当前价格伪造历史样本。

## 模块

```text
Compose UI
    │
ViewModel
    │
MacroRepository
    ├── EconomicCalendarClient  ── TradingView 日历 JSON（回退 Forex Factory 本周 JSON）
    ├── DirectMarketClient      ── Yahoo / Binance / BiQuote
    ├── LocalAnalysisEngine     ── 本地预期差与方向规则
    ├── AiAnalysisClient        ── 事件公布后的 AI 传导链路与走势分析
    ├── TranslationClient       ── 用户配置的 OpenAI 兼容接口
    └── Room EventDao           ── 事件、翻译、关注与 AI 分析缓存
```

## 日历

客户端按日期区间请求 TradingView 经济日历 JSON 接口（`economic-calendar.tradingview.com/events`，无需 API Key，请求带 `Origin` 头），并解析事件标题、国家（ISO 代码映射为全名）、分类（indicator）、发布时间、重要度（-1/0/1 映射为 1/2/3）和 Actual / Previous / Forecast。该接口只提供一个市场预期值（`forecast`），客户端会同时写入 `consensus` 与 `forecast`，否则规则引擎失去对比基准，会把所有事件判为中性信号。

TradingView 接口失败或返回空、且请求范围与当前周重叠时，回退请求 Forex Factory 公开周度 JSON（`nfs.faireconomy.media/ff_calendar_thisweek.json`，同样无需 Key），按 impact Low / Medium / High / Holiday 映射重要度，并将带单位字符串（如 `0.2%`、`768B`）归一化为数值与单位，同样把单一预期值写入 `consensus` 与 `forecast`。两个来源都不单独提供第二套共识值，属于已知数据边界。

事件 ID 由 provider ID 的 SHA-256 摘要稳定生成，以便刷新时 Room 可以覆盖同一事件。应用更新后首次刷新会清理旧 `trading_economics` 数据源的缓存行，避免同一事件重复展示。

日历按单日区间请求，日界与展示均采用**设备系统时区**：请求前把本地日转换为对应的 UTC 瞬时区间（例如东八区的 9 月 11 日 → `2026-09-10T16:00:00Z` ~ `2026-09-11T15:59:59Z`），终点用闭合边界，避免混入次日零点事件。首页“今日”、历史同步的 30 天窗口与 120 天清理阀值同样按系统时区。切换日期时会取消上一个请求、清空旧列表并显示加载提示，因此慢响应不会用其他日期的数据覆盖当前选择。

唯一例外是 Material3 日期选择器的毫秒值：`CalendarDatePickerState.selectedDateMillis` 按 API 约定用 UTC 编码“日期”而非“瞬时”，改成系统时区会让高亮日期偏移一天；它输出的 `LocalDate` 再按系统时区分桶。

## 行情与分析

- Binance 用于 BTC、ETH。
- BiQuote 优先用于贵金属、美元指数和 EUR/USD，失败后回退 Yahoo。
- Yahoo 用于指数与美债，并通过客户端请求闸门限制调用频率。
- 已发布事件尝试获取公布前后的一分钟 K 线；基准必须严格早于公布时间，目标样本不能晚于观察窗口。
- 无分钟样本、休市或超出数据保留期时返回缺失值。
- 本地规则引擎根据指标语义及 Actual - 预期（`consensus`，缺失时回退 `forecast`）生成 hawkish / dovish / neutral 信号，再与实际窗口变化分开显示。
- 历史与日历列表跨多天，历史卡片会同时显示日期（本地化短格式）与时间；单日视图的日期已在页头显示。

## AI 市场分析

事件公布数据后，分析页可调用用户配置的 OpenAI 兼容接口生成简报，输入包括事件数值（Actual / Consensus / Forecast / Previous）、本地规则信号、规则推导的预期方向以及公布后 1/5/15/30/60 分钟的可得行情窗口。模型返回结构化的 `chain`（传导链路：起点 → 终点、方向、理由）、`dataAnalysis`（数据解读）、`marketOutlook`（走势判断，含失效条件）与 `risks`，解析时容忍 `snake_case` 等命名差异，缺失端点的链路环节会被丢弃。

结果写入 Room 的 `ai_analysis` 表（每个事件一行，`revision` 递增），因此重新进入分析页会直接显示缓存结果；点「重新分析」会重新调用模型并覆盖缓存行。输出语言跟随应用语言（简中 / 繁中 / 英文）。未配置 Key 或事件尚未公布数据时不发起请求，仅显示提示。

## 翻译密钥

用户通过设置页填写 API Key、HTTPS endpoint 和模型。Key 只以 Android Keystore 生成的 AES-GCM 密钥加密后保存，不会进入 Room、日志、备份或源码。

语言策略：

1. 未配置 Key：应用启动时强制 English，中文选项禁用。
2. 已配置 Key：解锁简体中文和繁体中文。
3. 翻译以事件英文名去重，按每批 5 个名称、最多 4 批并发请求；每批完成后立即把原文与译文交给模型校对，只有被确认无误的条目才写入 Room，未通过的条目带改进提示重新翻译，最多 3 轮，仍未通过则保留英文。翻译全程在后台执行：列表先用缓存或英文渲染，译文入库后通知界面就地刷新，不阻塞页面；校对接口不可用时保留译文，同一会话内连续被拒的名称不再重复重试。
4. 详情页的人工勘正按事件名写回所有缓存行；日历与历史列表在重新进入页面时会从 Room 重读名称翻译，因此勘正结果会立即生效。
5. 删除 Key：立即切回 English，并锁定中文选项。
6. 翻译或校对失败：保留英文事件名，不阻断日历和行情刷新。

## 风险边界

客户端直连减少了项目服务器的攻击面和运维成本，但无法消除第三方依赖、限流、接口变更及移动网络限制。用户输入的 Key 也无法在已解锁或被调试的设备上获得绝对保护，因此只应使用权限受限、可撤销、有消费上限的个人 Key。
