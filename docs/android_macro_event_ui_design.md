# Android 端交互与页面设计方案

> 项目：宏观财经事件分析工具  
> 客户端：Android  
> 推荐技术：Kotlin + Jetpack Compose + Material 3  
> 定位：个人学习与宏观事件研究工具，而不是传统行情交易终端。

---

# 1. 产品定位

Android 端的核心目标不是简单复制 Trading Economics 财经日历，而是围绕以下完整流程设计：

```text
发现事件
   ↓
查看预期
   ↓
等待公布
   ↓
Actual 更新
   ↓
市场数据采集
   ↓
分析生成
   ↓
历史复盘
```

用户最关心的问题应该是：

- 今天有哪些重要财经事件？
- 下一项重要数据还有多久公布？
- Actual 和 Consensus 相差多少？
- 这个结果在宏观上偏 Hawkish 还是 Dovish？
- 黄金、美债、美元、美股、BTC 实际怎么走？
- 实际走势是否符合理论预期？
- 历史上类似事件通常如何表现？

---

# 2. 整体信息架构

推荐采用 5 个一级页面：

```text
Home
Calendar
Market
History
Settings
```

底部导航：

```text
┌─────────────────────────────────┐
│                                 │
│          当前页面内容            │
│                                 │
│                                 │
├─────────────────────────────────┤
│  首页   日历   市场   历史   设置 │
│   ●      ○      ○      ○      ○ │
└─────────────────────────────────┘
```

对应职责：

| 页面 | 作用 |
|---|---|
| Home | 今天最重要的财经事件、下一重要事件、实时状态 |
| Calendar | 查看未来一周和指定日期的财经事件 |
| Market | 查看宏观相关资产当前表现 |
| History | 查询历史 CPI、NFP 等事件后的市场反应 |
| Settings | 国家、事件等级、市场、通知与显示配置 |

核心二级页面：

```text
Event Detail
Analysis Detail
Historical Event Detail
Market Detail
```

---

# 3. 核心用户路径

推荐主路径：

```text
Home
 ↓
看到 US CPI
 ↓
Event Detail
 ↓
等待公布
 ↓
WebSocket 推送 Actual
 ↓
页面自动更新
 ↓
市场数据持续采集
 ↓
Analysis
 ↓
查看 1m / 5m / 15m / 30m / 60m 反应
 ↓
History
```

另外一条高频路径：

```text
Calendar
 ↓
选择日期
 ↓
筛选 High Importance
 ↓
Event Detail
```

---

# 4. Home 页面

Home 是使用频率最高的页面。

设计原则：

> 不展示所有信息，只突出“今天最值得关注的事件”。

页面顶部：

```text
┌────────────────────────────────┐
│ Macro                           │
│ 9月7日 · 星期一                 │
│                                │
│ 下一重要事件                    │
│ ┌────────────────────────────┐ │
│ │ 🇺🇸 美国 CPI              │ │
│ │                           │ │
│ │      还有 02:18:32        │ │
│ │                           │ │
│ │ Previous       2.7%       │ │
│ │ Consensus      2.9%       │ │
│ │ Forecast       2.8%       │ │
│ │                           │ │
│ │ 20:30        ● 高重要性    │ │
│ └────────────────────────────┘ │
│                                │
│ 今日事件                        │
│                                │
│ 18:00 🇪🇺 Retail Sales     ●● │
│ 20:30 🇺🇸 CPI              ●●●│
│ 22:00 🇺🇸 ISM Services     ●●●│
└────────────────────────────────┘
```

---

# 5. 下一重要事件倒计时

首页建议重点突出：

```text
US CPI

02:18:32
```

倒计时状态：

```text
> 1h       普通状态
< 30m      提醒状态
< 5m       即将公布
< 30s      Watching
已公布      Released
```

用户无需进入详情页即可感知市场事件节奏。

---

# 6. Event Card

普通事件卡片：

```text
┌──────────────────────────────────┐
│ 20:30   🇺🇸  US                  │
│                                  │
│ CPI YoY                          │
│                                  │
│ 前值 2.7%    预期 2.9%     ●●●  │
└──────────────────────────────────┘
```

公布后：

```text
┌──────────────────────────────────┐
│ 20:30   🇺🇸  US           已公布 │
│                                  │
│ CPI YoY                          │
│                                  │
│ 实际        3.2%                 │
│ 预期        2.9%                 │
│                                  │
│ ↑ 高于预期 +0.3%                 │
└──────────────────────────────────┘
```

卡片状态：

```text
Scheduled
Watching
Released
Analyzing
Completed
Timeout
```

---

# 7. 颜色与状态体系

不要把宏观事件简单设计成：

```text
绿色 = 好
红色 = 坏
```

因为不同资产对同一个宏观数据的反应完全不同。

推荐两套视觉语义。

## 宏观信号

```text
普通信息      蓝 / 灰
即将公布      橙
Hawkish      紫
Dovish       青
Neutral      灰
```

## 资产涨跌

```text
上涨         绿
下跌         红
无明显变化   灰
```

这样可以避免：

```text
CPI 高于预期 = “绿色”
```

这种错误语义。

---

# 8. Calendar 页面

Calendar 用于查看未来几天财经事件。

推荐顶部：

```text
<  9月7日 - 9月13日  >
```

日期选择：

```text
 MON     TUE     WED     THU     FRI
  7       8       9      10      11
 ●●      ●       ●●●     ●●      ●
```

点某一天：

```text
THU 10
```

立即切换该日事件。

---

# 9. Calendar 筛选

右上角：

```text
Filter
```

点击后使用 Bottom Sheet：

```text
┌────────────────────────────┐
│ 筛选                        │
│                            │
│ 重要性                      │
│ ☑ High                     │
│ ☑ Medium                   │
│ ☐ Low                      │
│                            │
│ 国家                        │
│ ☑ United States            │
│ ☑ Euro Area                │
│ ☑ China                    │
│ ☑ Japan                    │
│ ☐ UK                       │
│                            │
│ 类型                        │
│ ☑ Inflation                │
│ ☑ Employment               │
│ ☑ Central Bank             │
│ ☐ Housing                  │
│                            │
│          应用               │
└────────────────────────────┘
```

个人使用默认建议：

```text
US
EU
CN
JP

High
Medium
```

---

# 10. Event Detail 页面

Event Detail 是整个 App 最重要的页面之一。

公布前：

```text
┌──────────────────────────────────┐
│ ←   US CPI YoY             ☆     │
│                                  │
│ 🇺🇸 United States                │
│ Inflation                        │
│                                  │
│        20:30                     │
│       00:21:42                   │
│      距离公布时间                 │
│                                  │
├──────────────────────────────────┤
│ 数据                              │
│                                  │
│ Actual          --               │
│ Consensus       2.9%             │
│ Forecast        2.8%             │
│ Previous        2.7%             │
│                                  │
├──────────────────────────────────┤
│ 市场关注                          │
│                                  │
│ GOLD       --                    │
│ DXY        --                    │
│ US 2Y      --                    │
│ NASDAQ     --                    │
│ BTC        --                    │
│                                  │
├──────────────────────────────────┤
│ 历史                             │
│                                  │
│ 上次公布                         │
│ 2.7% vs 2.8%                    │
│                                  │
│ 查看历史反应 >                   │
└──────────────────────────────────┘
```

---

# 11. 关注与通知

右上角：

```text
☆
```

点击：

```text
★
```

表示关注事件。

关注后可以：

- 公布前提醒
- Actual 公布通知
- 5 分钟市场反应通知
- 15 分钟市场反应通知

---

# 12. 数据公布瞬间

原来：

```text
Actual
--
```

WebSocket 收到后自动变成：

```text
Actual
3.2%
```

页面顶部出现结果：

```text
┌────────────────────────────┐
│ 数据已公布                  │
│                            │
│ Actual         3.2%        │
│ Consensus      2.9%        │
│                            │
│ Surprise       +0.3%       │
│                            │
│ Strong Hawkish             │
└────────────────────────────┘
```

---

# 13. 公布后的分析进度

事件公布后分析不是一次完成。

推荐展示：

```text
正在观察市场反应...

Gold
DXY
US2Y
Nasdaq
BTC
```

时间阶段：

```text
1m      ✓
5m      03:42
15m     --
30m     --
60m     --
```

随着数据完成：

```text
1m      ✓
5m      ✓
15m     08:21
30m     --
60m     --
```

这样用户可以直观看到分析进度。

---

# 14. Analysis 页面

Analysis 分为四个核心区域。

---

## 14.1 数据结果

```text
US CPI YoY

Actual          3.2%
Consensus       2.9%
Previous        2.8%

Surprise
+0.3%

Strong Hawkish
```

---

## 14.2 理论影响

```text
理论市场影响

USD             ↑
US 2Y           ↑
US 10Y          ↑

Gold            ↓
NASDAQ          ↓
```

附注：

```text
基于宏观规则和历史规律推断，
不代表市场实际一定按此方向运行。
```

---

## 14.3 实际行情

推荐表格：

| Asset | 1m | 5m | 15m |
|---|---:|---:|---:|
| Gold | -0.31% | -0.94% | -1.10% |
| DXY | +0.18% | +0.53% | +0.62% |
| US2Y | +4bp | +8bp | +11bp |
| NASDAQ | -0.10% | +0.20% | -0.35% |
| BTC | -0.42% | -1.20% | -1.56% |

每个资产附加：

```text
✓ 符合
```

或者：

```text
≠ 背离
```

例如：

```text
Gold

理论 ↓
实际 -0.94%

✓ 符合
```

---

# 15. Expected 与 Observed 分离

页面一定区分：

```text
Expected Reaction
Observed Reaction
```

不要写：

```text
CPI 高于预期，所以黄金一定下跌。
```

应该：

```text
理论倾向：
Gold ↓

实际：
Gold -0.94%

判断：
符合典型 Hawkish 市场反应。
```

---

# 16. 市场反应时间轴

建议 Analysis 增加一个交互式时间轴。

例如：

```text
       CPI
        │
        ▼
────────●─────────────────────
        T0

 -5m      0      +5m      +15m
```

用户可以拖动：

```text
       │
       ▼
───────●──────────────────────
      +3m 12s
```

显示：

```text
Gold
-0.72%

DXY
+0.35%

US2Y
+6bp
```

多个资产可以同步移动。

---

# 17. Market 页面

Market 页面不需要做完整 TradingView。

核心用途：

> 快速查看宏观事件相关资产正在发生什么。

推荐分类：

```text
风险资产
贵金属
美元
美债
能源
Crypto
```

例如：

```text
市场

风险资产
┌──────────────────────┐
│ NASDAQ     18,420    │
│ +0.72%               │
│ ▁▂▃▅▇▆                │
└──────────────────────┘

贵金属
┌──────────────────────┐
│ GOLD       2345.2    │
│ -0.31%               │
└──────────────────────┘

美元
DXY          +0.21%

美债
US 2Y        4.11%
US 10Y       4.24%

Crypto
BTC          $65,200
```

---

# 18. 关联财经事件

Market Detail 页面建议展示：

```text
今日相关宏观事件
```

例如 Gold：

```text
20:30 US CPI
22:00 ISM Services
```

点击事件：

```text
Market
 ↓
Related Event
 ↓
Event Detail
```

形成：

```text
市场 ←→ 财经事件
```

双向关联。

---

# 19. History 页面

History 用于宏观事件研究。

顶部：

```text
历史研究

指标
[ US CPI ▼ ]

时间
[ 最近24次 ▼ ]
```

基础统计：

```text
24 次事件

Actual > Consensus
14 次

Actual < Consensus
8 次

Equal
2 次
```

---

# 20. 条件历史统计

例如：

```text
当 Surprise > +0.2%

事件数量      8

Gold 5m
平均          -0.63%

DXY 5m
平均          +0.41%

US2Y
平均          +7.4bp

NASDAQ
平均          -0.51%
```

未来可以支持：

```text
Surprise > 0
Surprise > +0.1
Surprise > +0.2
Surprise < -0.1
```

---

# 21. Historical Event Detail

例如：

```text
2026-08-12
US CPI YoY
```

详情：

```text
Actual       3.1%
Consensus    2.9%
Previous     2.8%

Surprise     +0.2%
```

市场：

```text
Gold

-1m         2355
+1m         2348
+5m         2339
+15m        2342
+60m        2350
```

并提供：

```text
查看完整市场时间轴
```

这一能力可以视为：

```text
Macro Event Replay
```

---

# 22. 通知设计

建议通知分为三类。

---

## 22.1 公布前

```text
🇺🇸 US CPI

将在 5 分钟后公布

Consensus: 2.9%
Previous: 2.7%
```

点击进入 Event Detail。

---

## 22.2 公布后

```text
🇺🇸 US CPI 已公布

Actual        3.2%
Consensus     2.9%

高于预期 +0.3%

Strong Hawkish
```

---

## 22.3 市场反应

```text
US CPI · 15分钟市场反应

Gold      -1.10%
DXY       +0.62%
US2Y      +11bp
NASDAQ    -0.35%

查看分析 →
```

---

# 23. Settings 页面

建议保持简单。

## 国家

```text
☑ United States
☑ Euro Area
☑ China
☑ Japan
☐ United Kingdom
```

## 事件等级

```text
☑ High
☑ Medium
☐ Low
```

## 市场观察

```text
☑ Gold
☑ DXY
☑ US2Y
☑ US10Y
☑ Nasdaq
☑ Bitcoin
```

## 通知

```text
事件前通知

[ 5分钟 ▼ ]

☑ 数据公布通知
☑ 5分钟市场反应
☑ 15分钟市场反应

提醒级别

[ High Importance ▼ ]
```

---

# 24. Android Navigation

推荐：

```text
BottomNavigation

Home
│
├── Event Detail
│       ├── Analysis
│       └── Historical Detail
│
Calendar
│
└── Event Detail
│
Market
│
├── Market Detail
│
└── Related Event
│
History
│
├── Indicator History
│
└── Historical Event
│
Settings
```

---

# 25. Compose 页面结构

推荐目录：

```text
ui/

├── home/
│   ├── HomeScreen.kt
│   ├── UpcomingEventCard.kt
│   └── TodayEventList.kt
│
├── calendar/
│   ├── CalendarScreen.kt
│   ├── DateSelector.kt
│   ├── CalendarFilterSheet.kt
│   └── EventCard.kt
│
├── event/
│   ├── EventDetailScreen.kt
│   ├── ReleaseDataCard.kt
│   ├── CountdownCard.kt
│   ├── EventStatusCard.kt
│   └── MarketTrackingCard.kt
│
├── analysis/
│   ├── AnalysisScreen.kt
│   ├── MacroSignalCard.kt
│   ├── ExpectedReactionCard.kt
│   ├── MarketReactionTable.kt
│   └── ReactionTimeline.kt
│
├── market/
│   ├── MarketScreen.kt
│   ├── MarketCard.kt
│   └── MarketDetailScreen.kt
│
├── history/
│   ├── HistoryScreen.kt
│   ├── HistoricalStats.kt
│   ├── IndicatorHistoryScreen.kt
│   └── HistoricalEventScreen.kt
│
└── settings/
    └── SettingsScreen.kt
```

---

# 26. ViewModel 建议

可以对应：

```text
HomeViewModel
CalendarViewModel
EventDetailViewModel
AnalysisViewModel
MarketViewModel
HistoryViewModel
SettingsViewModel
```

状态采用：

```text
StateFlow
```

事件采用：

```text
SharedFlow
```

---

# 27. 数据层建议

Android：

```text
Compose UI
    ↓
ViewModel
    ↓
Repository
    ↓
┌─────────────┬──────────────┐
│ REST API    │ WebSocket    │
│ Retrofit    │ OkHttp       │
└─────────────┴──────────────┘
       ↓
Room Cache
```

Room 用于：

- 缓存近期财经日历
- 用户筛选设置
- 已关注事件
- 最近查看历史
- 网络断开时展示最后数据

核心真实数据仍以后端为准。

---

# 28. WebSocket 交互

推荐后端推送：

```text
economic_event_released
analysis_progress
analysis_completed
market_snapshot_updated
```

例如：

```json
{
  "type": "economic_event_released",
  "eventId": 123,
  "actual": "3.2",
  "consensus": "2.9"
}
```

Android：

```text
WebSocket
   ↓
Repository
   ↓
StateFlow
   ↓
Compose 自动重组
```

无需用户手动刷新。

---

# 29. UI 风格

推荐：

```text
Material 3
+
TradingView 风格的信息密度
```

但不要做成传统交易终端。

视觉特点：

- 默认支持 Dark Mode
- 深灰 / 黑背景
- 卡片层级较弱
- 大数字突出
- 时间、Actual、Consensus 强调
- 重要 Signal 使用少量高饱和颜色
- 避免满屏红绿
- 图表简洁
- 保持较高信息密度

---

# 30. 字体层级

建议：

```text
事件名称         Title Medium / Large
Actual           Display Small / Headline
倒计时           Headline Large
Consensus        Body Large
时间 / 国家      Label Medium
辅助说明         Body Small
```

最重要的数字：

```text
Actual
Surprise
Countdown
Market Change
```

应该有明显视觉层级。

---

# 31. MVP 页面范围

第一版不要同时实现全部页面。

推荐只完成：

```text
Home
Calendar
Event Detail
Analysis
```

即可跑通：

```text
事件发现
→ 事件详情
→ 等待数据
→ Actual 推送
→ 市场反应
→ Analysis
```

---

# 32. 第二阶段

增加：

```text
Notifications
Market
```

功能：

- 公布前提醒
- Actual 通知
- 5m / 15m 市场反应
- 当前宏观市场状态

---

# 33. 第三阶段

增加：

```text
History
Historical Statistics
Macro Event Replay
```

这会逐渐形成真正的：

```text
Macro Research Tool
```

---

# 34. 最终页面关系

```text
                       ┌──────────────┐
                       │     Home     │
                       └──────┬───────┘
                              │
                    ┌─────────▼──────────┐
                    │    Event Detail    │
                    └─────────┬──────────┘
                              │
                       数据公布 / 分析
                              │
                    ┌─────────▼──────────┐
                    │      Analysis      │
                    └─────────┬──────────┘
                              │
                              ▼
                           History


┌──────────────┐
│   Calendar   │──────→ Event Detail
└──────────────┘


┌──────────────┐
│    Market    │──────→ Market Detail
└──────┬───────┘
       │
       └──────────────→ Related Event


┌──────────────┐
│   History    │──────→ Historical Event
└──────────────┘
```

---

# 35. 最重要的交互设计原则

## 1. 首页突出下一重要事件

用户打开 App，第一眼应该知道：

```text
下一件真正值得关注的事情是什么？
```

---

## 2. 数据公布后自动更新

不要依赖：

```text
下拉刷新
```

而应该：

```text
WebSocket
→ Actual
→ Compose 自动更新
```

---

## 3. 分析过程可见

市场分析需要时间：

```text
1m
5m
15m
30m
60m
```

应该明确显示进度，而不是让用户以为程序卡住。

---

## 4. 理论与实际分开

必须明确：

```text
Expected
Observed
```

这是整个产品非常重要的认知设计。

---

## 5. 历史复盘不是附件

History 最终应该成为核心能力，而不是简单的数据列表。

目标：

```text
这一次 CPI
和历史上类似的 CPI Surprise
有什么不同？
```

---

# 36. 推荐 MVP

最小 Android MVP：

```text
Home
+
Calendar
+
Event Detail
+
Analysis
+
WebSocket
+
Notification
```

数据：

```text
US High Importance
```

重点事件：

```text
CPI
Core CPI
NFP
Unemployment Rate
PCE
Core PCE
GDP
FOMC
ISM
Initial Jobless Claims
EIA Crude Oil Inventories
```

观察市场：

```text
Gold
DXY
US2Y
US10Y
NASDAQ
BTC
```

---

# 37. 最终产品定位

最终 Android App 不应该只是：

> 财经日历 App

而应该是：

> 宏观财经事件研究与复盘工具

完整能力：

```text
Economic Calendar
      +
Event Countdown
      +
Actual / Consensus
      +
Macro Signal
      +
Market Reaction
      +
Historical Statistics
      +
Replay
```

最终帮助用户回答：

```text
发生了什么？
为什么重要？
理论应该怎么走？
实际怎么走？
为什么不一样？
历史上通常怎么走？
```
