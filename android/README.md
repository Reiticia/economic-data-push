# Macro Research Android

无需项目服务端的宏观财经事件研究客户端。

## 功能

- 五个一级导航：首页、日历、市场、历史、设置。
- 客户端直接抓取 Trading Economics 公开日历并写入 Room 缓存。
- 客户端直接从 Yahoo Finance、Binance、BiQuote 获取实时行情和可用的分钟 K 线。
- 在设备端计算 Actual / Consensus 预期差、规则信号、理论影响和实际行情窗口。
- 首页下一重要事件、秒级倒计时、今日事件和市场概览。
- 七日选择、重要性与国家筛选的财经日历。
- 历史页读取最近公开日历数据及本机缓存；公开行情超出保留期时明确显示缺失，不使用当前价格伪造历史数据。
- Room 离线缓存与本地关注状态。
- English / 简体中文 / 繁體中文界面。

## 翻译与 API Key

应用默认固定为英文。设置页提供：

- OpenAI 兼容 API Key
- HTTPS API endpoint
- 模型名称

只有成功保存非空 Key 后，简体中文和繁体中文选项才可选择。事件名称由客户端直接调用用户配置的翻译接口；翻译失败不会影响日历和行情数据。

安全措施：

- APK 与源码不包含用户 Key。
- Key 使用 Android Keystore 的 AES-GCM 密钥加密后写入独立 SharedPreferences。
- 密钥文件排除云备份和设备迁移。
- 翻译 endpoint 必须使用 HTTPS，且不能包含用户信息、查询参数或 fragment。
- 调试日志只记录 HTTP BASIC 元数据，不记录 Authorization header。
- 建议使用有权限范围和消费限额的个人 Key。

## 第三方数据源

客户端内只包含公开第三方服务入口，不包含自有服务器地址：

- `tradingeconomics.com`：财经日历
- `query1.finance.yahoo.com`：传统市场行情
- `data-api.binance.vision`：Binance 公共加密资产行情
- `biquote.io`：外汇与贵金属行情

第三方服务可能限流、调整页面结构或缩短历史数据保留期。应用使用本地缓存、逐源降级和明确缺失状态，但不能保证第三方长期可用。使用时应遵守对应服务条款。

## 构建与测试

需要 JDK 17：

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug assembleDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```
