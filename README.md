<div align="center">

# HyperNavBar

### 小米 HyperOS 小白条沉浸规则管理工具

[规则文档](docs/rule.md) | [更新日志](changelog.md) | [规则仓库](https://github.com/Ianzb/HyperNavBarRules) | [Telegram 群组](https://t.me/HyperNavBar)

[![GitHub License](https://img.shields.io/github/license/Ianzb/HyperNavBar)](LICENSE)
[![GitHub Issues](https://img.shields.io/github/issues/Ianzb/HyperNavBar)](https://github.com/Ianzb/HyperNavBar/issues)
[![GitHub PRs](https://img.shields.io/github/issues-pr/Ianzb/HyperNavBar)](https://github.com/Ianzb/HyperNavBar/pulls)

</div>

**HyperNavBar** 是一款小米 HyperOS 导航栏沉浸规则管理应用，通过 Root 权限自定义应用的小白条行为。

规则数据由 [HyperNavBar Rules](https://github.com/Ianzb/HyperNavBarRules) 仓库维护，欢迎前往贡献。

<br>

# 交流 & 反馈群组

[![tg_badge]][tg_url]

加入群组反馈问题、了解最新动态、与其他贡献者交流。

<br>

# 功能

- **多云订阅支持** — 添加云端 JSON 订阅源或本地 JSON，自动拉取并解析 `NBIRules`
- **优先级合并** — 多订阅源按优先级智能合并，按包名去重
- **智能格式转换** — 自动检测 HyperOS 版本（OS22 / OS30 / OS33），输出对应格式（XML / JSON）
- **一键应用 & 热重载** — Root 写文件 + `cmd miui_navigation_bar_immersive update` 即时生效
- **还原官方规则** — 支持备份 / 恢复系统默认规则
- **开机自动应用** — 通过 `BootReceiver` 开机后自动应用规则
- **3 种导航栏样式** — 标准 / 悬浮 / 液态玻璃（iOS 风格液态玻璃效果）
- **6 种主题模式** — 系统跟随 / 浅色 / 深色 / Monet 跟随 / Monet 浅色 / Monet 深色
- **中英文双语** — 支持简体中文和英文，跟随系统语言
- **设置导入导出** — 一键备份 / 恢复全部配置

<br>

# 系统要求

- 小米设备，已刷 **HyperOS**（2.2 / 3.0 / 3.3+）
- **Root 权限**（Magisk / KernelSU / APatch）
- Android 15+（minSdk 35）

<br>

# 下载

前往 [Releases](https://github.com/Ianzb/HyperNavBar/releases) 下载最新 APK。

<br>

# 构建

```bash
# 克隆项目
git clone https://github.com/ianzb/HyperNavBar.git
cd HyperNavBar

# 设置 JDK 17+ 和 Android SDK
# 编辑 local.properties 指向你的 SDK 路径

# 构建 Debug APK
./gradlew assembleDebug

# APK 输出位置
# app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

<br>

# 如何贡献

[![tg_badge]][tg_url]

### 相关信息

[点击此处](docs/rule.md) 查看规则编写说明  
[点击此处](changelog.md) 查看更新日志  
[点击此处](https://github.com/Ianzb/HyperNavBarRules) 查看规则仓库  
[前往 Telegram 群组](https://t.me/HyperNavBar) 与其他贡献者交流

### 遇到问题？

前往 [Issue](https://github.com/Ianzb/HyperNavBar/issues) 或 [Telegram 群组](https://t.me/HyperNavBar) 反馈。

### 参与贡献

- [提交 Pull Request](https://github.com/Ianzb/HyperNavBar/pulls) 贡献代码
- [规则仓库](https://github.com/Ianzb/HyperNavBarRules) 贡献适配规则
- 查看 [规则文档](docs/rule.md) 了解规则编写说明

<br>

# 第三方库

- [Miuix](https://github.com/YuKongA/Miuix) — HyperOS 风格 Compose UI 组件库
- [AndroidX Compose](https://developer.android.com/jetpack/compose) — 声明式 UI 框架
- [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) — 液态玻璃导航栏效果参考
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) — 异步支持

<br>

# 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源。

<br>

# Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Ianzb/HyperNavBar&type=Date)](https://www.star-history.com/#Ianzb/HyperNavBar&Date)

[tg_badge]: https://img.shields.io/badge/TG-群组-4991D3?logo=telegram
[tg_url]: https://t.me/HyperNavBar
