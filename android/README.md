# Macro Research Android

宏观财经事件研究客户端，视觉参考 `docs/ChatGPT Image 2026年9月7日 14_46_30.png`，交互与页面范围参考 `docs/android_macro_event_ui_design.md`。

## 已实现

- 五个一级导航：首页、日历、市场、历史、设置。
- 首页下一重要事件、秒级倒计时、今日事件和事件窗口市场概览。
- 七日选择、重要性与国家筛选的财经日历。
- 事件详情、关注状态、Actual/Consensus、市场采集进度与历史入口。
- 分析结果、Hawkish/Dovish 信号、Expected/Observed 分离、五周期行情表和可拖动时间轴。
- Retrofit REST、OkHttp WebSocket 自动重连、Room 离线缓存。
- Actual 发布和分析完成系统通知。
- 深色高信息密度主题；宏观信号与资产涨跌使用不同色彩语义。

## 运行

先启动 Rust 后端：

```bash
cd backend
cargo run
```

再用 Android Studio 打开 `android/`，或运行：

```bash
cd android
./gradlew assembleDebug
```

模拟器默认通过 `http://10.0.2.2:8080` 访问宿主机后端。真机调试时，在 `app/build.gradle.kts` 中把 `API_BASE_URL` 和 `WS_URL` 改为电脑的局域网地址。

独立 Market 页面需要后端补充当前报价 API；当前可用的事件窗口行情已经在首页、事件详情和分析页面展示。
