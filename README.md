# Macro Research

纯 Android 的宏观财经事件研究客户端，不需要部署或维护项目自建服务器。

```text
macro-research/
├── android/   Kotlin、Jetpack Compose、Room 客户端
└── docs/      客户端数据流与安全设计
```

## 数据流

Android 客户端直接获取并处理公开数据：

- 财经日历：TradingView 经济日历 JSON 接口，失败时回退 Forex Factory 公开 JSON
- 美债收益率：CNBC 报价与 1 分钟 K 线，失败时回退 Yahoo Finance
- 股票指数、外汇与贵金属：BiQuote，失败时回退 Yahoo Finance
- 加密资产：Binance
- 可选 HTTP 代理：仅用于日历与行情请求，便于在受限网络下访问上述源；AI 与翻译请求不经代理
- 事件分析：设备端规则引擎
- AI 市场分析：用户配置的 OpenAI 兼容接口，事件公布后生成传导链路与走势分析，本地缓存且可重新生成
- 缓存：Room 本地数据库
- 事件名称翻译：用户自行配置的 OpenAI 兼容 API

APK 中不包含私人 API Key 或项目服务器地址。未配置翻译 Key 时应用固定使用英文；Key 由 Android Keystore 加密保存后，才会解锁简体中文和繁体中文。

## 构建

需要 JDK 17 和 Android SDK：

```bash
cd android
./gradlew assembleDebug
```

详见 [Android 客户端说明](android/README.md) 和 [客户端架构](docs/client_architecture.md)。

## GitHub Release 自动构建

支持 Actions 手动构建签名 APK，以及推送 Tag 时自动发布到 GitHub Releases。
首次使用需配置四项签名 Secrets，详见 [Release 构建与发布说明](docs/github-release.md)。
