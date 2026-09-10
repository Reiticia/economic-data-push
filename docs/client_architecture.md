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
    ├── TradingEconomicsClient  ── 公开日历 HTML
    ├── DirectMarketClient      ── Yahoo / Binance / BiQuote
    ├── LocalAnalysisEngine     ── 本地预期差与方向规则
    ├── TranslationClient       ── 用户配置的 OpenAI 兼容接口
    └── Room EventDao           ── 事件、翻译与关注缓存
```

## 日历

客户端按日期请求公开日历页面，并使用 DOM 选择器读取事件、国家、分类、发布时间、重要性和 Actual / Previous / Consensus / Forecast。事件 ID 由 provider ID 的 SHA-256 摘要稳定生成，以便刷新时 Room 可以覆盖同一事件。

## 行情与分析

- Binance 用于 BTC、ETH。
- BiQuote 优先用于贵金属、美元指数和 EUR/USD，失败后回退 Yahoo。
- Yahoo 用于指数与美债，并通过客户端请求闸门限制调用频率。
- 已发布事件尝试获取公布前后的一分钟 K 线；基准必须严格早于公布时间，目标样本不能晚于观察窗口。
- 无分钟样本、休市或超出数据保留期时返回缺失值。
- 本地规则引擎根据指标语义及 Actual - Consensus 生成 hawkish / dovish / neutral 信号，再与实际窗口变化分开显示。

## 翻译密钥

用户通过设置页填写 API Key、HTTPS endpoint 和模型。Key 只以 Android Keystore 生成的 AES-GCM 密钥加密后保存，不会进入 Room、日志、备份或源码。

语言策略：

1. 未配置 Key：应用启动时强制 English，中文选项禁用。
2. 已配置 Key：解锁简体中文和繁体中文。
3. 翻译以事件英文名去重并分批请求，结果缓存在 Room。
4. 删除 Key：立即切回 English，并锁定中文选项。
5. 翻译失败：保留英文事件名，不阻断日历和行情刷新。

## 风险边界

客户端直连减少了项目服务器的攻击面和运维成本，但无法消除第三方依赖、限流、DOM 变更及移动网络限制。用户输入的 Key 也无法在已解锁或被调试的设备上获得绝对保护，因此只应使用权限受限、可撤销、有消费上限的个人 Key。
