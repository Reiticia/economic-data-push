# Macro Research Android

宏观财经事件研究客户端，视觉参考 `docs/ChatGPT Image 2026年9月7日 14_46_30.png`，交互与页面范围参考 `docs/android_macro_event_ui_design.md`。

## 已实现

- 五个一级导航：首页、日历、市场、历史、设置。
- 市场页通过后端批量行情接口展示价格、日涨跌、日内高低和休市/陈旧状态，并每30秒刷新。
- 首页下一重要事件、秒级倒计时、今日事件和事件窗口市场概览。
- 七日选择、重要性与国家筛选的财经日历。
- 历史页跨国家查询、刷新及分页加载；历史分析展示行情来源、K线精度、可用窗口与缺失原因，明确修订数据风险。
- 过去三个月的补采由后端 `--backfill` 命令启动，需要有效历史数据源权限，详见 [后端文档](../docs/backend.md#过去三个月日历与事件窗口涨跌补采)。
- 事件详情、关注状态、Actual/Consensus、市场采集进度与历史入口。
- 分析结果、Hawkish/Dovish 信号、Expected/Observed 分离、五周期行情表和可拖动时间轴。
- Retrofit REST、OkHttp WebSocket 自动重连、Room 离线缓存。
- Actual 发布和分析完成系统通知。
- 墨绿 / 玉石青 / 香槟金主题，随系统切换深浅色；浅色使用暖白底与深青文字。
- 设置页支持 English / 简体中文 / 繁體中文，立即切换并持久记忆；固定文案、国家、资产、宏观信号、通知、日期及数值单位跟随语言。未知接口值与事件名称保留原文。

## 配色与界面文案

- `app/src/main/java/com/macroresearch/ui/theme/Colors.kt`：深浅色面板、文字与容器配对，以及独立的财经语义色。
- 玉石青用于交互与选中态，金色用于重要事件；紫色偏鹰、蓝色偏鸽，绿色上涨、红色下跌。文字和箭头同时保留，不仅依赖颜色传达信息。
- `app/src/main/java/com/macroresearch/ui/common/Labels.kt`：共用标签资源映射，仅转换展示文案，不修改接口参数或筛选键。
- `app/src/main/res/values/strings.xml`、`values-zh-rCN/strings.xml`、`values-zh-rTW/strings.xml`：英文、简体、繁体文案。新增文案需同时维护三份资源。
- `app/src/main/java/com/macroresearch/ui/settings/LanguageSettings.kt`：使用 AppCompat 应用语言 API；Android 13+ 同步系统应用语言设置，Android 8–12 由 AppCompat 自动存储。首次运行跟随系统支持的语言，不匹配时使用英文。
- `./gradlew :app:testDebugUnitTest :app:lintDebug`：检查深浅色文本对比度（至少 4.5:1）、三语资源完整性与格式参数、标签映射和本地化数值单位。构建需 JDK 17。

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

独立 Market 页面调用后端 `/api/v1/market/quotes`；客户端不直接持有或调用第三方行情地址。
