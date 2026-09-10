# Economic Data Push

纯 Android 的宏观财经事件研究客户端，不需要部署或维护项目自建服务器。

```text
economic-data-push/
├── android/   Kotlin、Jetpack Compose、Room 客户端
└── docs/      客户端数据流与安全设计
```

## 数据流

Android 客户端直接获取并处理公开数据：

- 财经日历：Trading Economics 公开日历页面
- 股票指数与美债：Yahoo Finance
- 加密资产：Binance
- 外汇与贵金属：BiQuote，失败时回退 Yahoo Finance
- 事件分析：设备端规则引擎
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
