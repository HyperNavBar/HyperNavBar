# 开发规范（组件与交互）

> 本文档同步自脚手架 [MiuixGuiTemplate](https://github.com/Ianzb/MiuixGuiTemplate) 0.5.4 的接口文档（`docs/API.md`）与二次开发指南（`docs/CUSTOMIZE.md`）中与本项目相关的通用规范，组件名与路径已按本项目调整。修改相关代码前请先阅读本文档。

<br>

## 1. 配置项与依赖

### 1.1 依赖前置：始终显示、禁用灰显

需要前置选项解锁的功能**必须始终显示**；前置未满足时统一为**禁用（灰显）**，**禁止隐藏**——不得使用 `AnimatedVisibility` 或条件渲染按配置值隐藏组件或分区。依赖一律用 `OptionSpec.dependsOn`（`+ dependsOnValue`）声明，由 `rememberDependencyEnabled(spec)` 统一判定 `enabled`。

### 1.2 多级依赖链

依赖支持**多级链**（如 总开关 → 功能开关 → 滑条）：`rememberDependencyEnabled` 会沿 `dependsOn` 向上逐级检查，链上任意一环节未满足时，组件同样置灰不可交互。

<br>

## 2. 滑条（`HookSliderCard`）

- `masterKey` 非空时，卡片顶部渲染一个 `SwitchPreference`（标题取 `spec.titleRes`），其开关状态**只控制滑块启用 / 禁用**（`enabled = enabled && masterEnabled`）；滑块**始终显示**，不随开关隐藏。
- **标题必须用 `sliderValueLabelRes`（可直接复用 `titleRes` 的字符串资源）**：该标签显示在滑块行**数值左侧**，与滑块同属一个组件；`sliderUnitRes` 显示在数值**右侧**作为单位后缀（如 `%`、`dp`）。两者都不设时，组件才会退化为「滑动组件外部的独立标题」（规范不推荐）。
- 点击滑块行空白弹出居中输入对话框：最小值 / 最大值分行显示、当前值与默认值同行，「取消 / 恢复默认 / 确定」三键。

<br>

## 3. 功能页顶部提示区（`HookOptionsPage.topContent`）

`topContent` 渲染在搜索栏**上方**、搜索展开时隐藏，用于放置系统版本要求等说明卡片；为空则不显示（参见功能页的 `FeatureNotice`）。

<br>

## 4. 主页 Pager 手势与横向拖拽子组件

主页 `HorizontalPager` 采用 Cross-Axis 拦截模式（`userScrollEnabled = false` + 自实现拖拽接管，见 `MainActivity`）。该模式在 `PointerEventPass.Initial` 阶段（父级先收到事件）消费横向拖拽，**页面内需要横向拖动的组件（如滑条）默认收不到手势、无法拖动**。

本项目在 `ui/util/PagerSwipeOverride.kt`（改编自 miuix `PagerGestureUtils.horizontalPagerSwipeOverride`，Apache-2.0）提供放行机制：

- **Pager 侧**：以 `Modifier.pagerSwipeOverride(pagerState = pagerState)` 替代 miuix 的 `pagerGestureOverride`；其余配置（`userScrollEnabled = false`、`pageNestedScrollConnection = PagerGestureNestedScrollConnection`）保持不变。
- **子组件侧**：`Modifier.claimPagerSwipe()` 在按下时同步「认领」本次手势（只观察、不消费事件），被认领的手势 Pager 不再拦截翻页。`HookSliderCard` 已内置；自定义横向拖拽子组件必须手工挂上。
- 非拖拽区域的左右滑动切页、惯性分页、触控板滚轮 / Pan 翻页、无障碍翻页均不受影响。

<br>

## 5. Root 检测时机

`XposedServiceManager.checkRoot()` 会真正拉起一次 `su`，在部分 Root 管理器上会弹出授权 / 使用提示，因此只在两个时机调用：

1. 应用启动时（`XposedServiceManager.init()`，以及主页启动时的一次检测）；
2. 用户在主页**手动点按状态卡片**时。

**不要**做定时轮询，也不要在每次回到前台时检测；其余场景一律复用上一次结果（`isRootAvailable` / `rootChecked`）。

<br>

## 6. 跨进程配置读取（`HookPrefs.registerListener`）

Hook 侧 `HookPrefs` 提供 `registerListener(onChange)`，用于需要「改配置即时生效」的功能。宿主 / 框架不支持偏好变化监听时会**静默忽略**，此时功能退化为目标进程重启后生效——因此**不要把它当作唯一生效路径**，仍要在使用点按当前配置读取（或 `init()` 时）套用一次。
