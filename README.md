# Economic Data Push

宏观财经事件采集与市场反应分析项目。工程按职责拆分为三个独立区域：

```text
economic-data-push/
├── backend/   Rust 后端、数据库迁移、配置、测试与部署文件
├── android/   Android 客户端工程入口
└── docs/      产品设计与后端使用文档
```

## 快速入口

- [项目设计](docs/rust_macro_event_analyzer_design.md)
- [后端说明](docs/backend.md)
- [Android 客户端说明](android/README.md)

启动后端：

```bash
cd backend
cargo run
```

