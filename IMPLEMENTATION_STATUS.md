# 实现进度与已知偏差（2026-11-10）

对应《OmniBox-Android开发计划.md》的里程碑。**2026-11-10 本机已装齐 JDK 17 / Android SDK 35 / Gradle 8.9 Wrapper，
`:app:assembleDebug` 真实编译通过并产出可安装的 debug APK**；下方状态以此为准。

## 里程碑

| 阶段 | 计划内容 | 状态 |
|---|---|---|
| P0 | 固化设计基线、抽取 25 个 Kotlin 代码块、建立工程根目录 | ✅ 完成（实际抽出 26 个，含 `_16.md` 的 ToolDefinition 片段） |
| P1 | 设计系统、模块拓扑、主框架 6 屏、4 款无权限工具 | ✅ 完成，已通过真实编译 |
| P2 | 扫码 / 图片压缩 / EXIF / 权限与降级、技术验证 | ⏳ 未开始 |
| P3–P7 | 其余工具、前台服务、Glance 小组件、32 屏截图比对、release 包 | ⏳ 未开始 |

## 工程骨架

- 18 个 Gradle 模块（1 app + 4 core + 13 feature），与计划 §6.2 一致；`feature:texttools` 同时承载 `text_tools` 与 `dev_tools`。
- 版本目录 `libs.versions.toml`：32 个 library / 6 个 plugin；Kotlin DSL 全模块统一。
- Hilt + Room(KSP) + DataStore + Navigation Compose 全部按计划接入。
- `reference/kt/` 26 个 `.kt` 为参考源码，**刻意不纳入 Gradle 编译**：这些片段来自设计文档，彼此引用不自洽、含 TODO 占位，直接编译必然失败。已按算法思路重写为工程内自洽实现。

## 13 款工具

| 工具 | 页面 ID | 状态 | 真实实现 |
|---|---|---|---|
| 二维码 | `qr_scan_active` 等 5 屏 | 排期 P2 | 注册表已接入，点击显示明确状态页 |
| 单位换算 | `unit_converter` | ✅ 可用 | `BigDecimal` 七类物理量、温度 offset、离线汇率快照、精度档位、基准速查 |
| 随机转盘抽签 | `random_wheel` / `wheel_editor` | 排期 P5 | Room 表已建 |
| 倒计时/纪念日 | `countdown_*` 3 屏 | 排期 P5 | Room 表已建 |
| 图片无损压缩 | `image_compress` | 排期 P2 | Room 表已建；`oom_2` 参考源码已归档 |
| EXIF 隐私擦除 | `exif_*` 2 屏 | 排期 P3 | — |
| 指南针/水平仪 | `compass_*` 2 屏 | 排期 P4 | — |
| 环境分贝计 | `sound_meter` / `sound_permission` | 排期 P4 | — |
| 格式转换 | `format_converter` 等 3 屏 | 排期 P3 | 转码任务 / 产物 Room 表已建 |
| 安全密码生成 | `password` | ✅ 可用 | `SecureRandom`、熵值、破解耗时、字典校验、Diceware、批量生成、复制 |
| 时间戳转换 | `timestamp` | ✅ 可用 | `java.time` 秒/毫秒双向、5 时区、ISO/周序数、时间差、常量表 |
| 文本全能箱 | `text_tools` / `text_tools_empty` | ✅ 可用 | MD5/SHA、Base64、URL、7 种清洗、字符统计、空态 |
| 代码与格式化 | `dev_tools` | ✅ 可用 | JSON 美化校验、进制联动、正则匹配/捕获组、URL 参数拆解 |

## 33 屏覆盖

| 类别 | 数量 | 状态 |
|---|---|---|
| 主框架 6 屏（home / favorites / settings / tool_settings / tool_history / search_result） | 6 | ✅ 已按 `code.html` 文案还原，控件接真实数据 |
| 4 款工具页 + 开发者箱 + 文本空态 | 6 | ✅ 已还原，`dev_tools` 与 `text_tools_empty` 各占一屏 |
| `omnibox_offline_utility_suite` | 1 | 计划裁定为旧版首页副本，不实现（该目录亦无截图） |
| 其余 20 屏 | 20 | ⏳ 按 P2–P7 接入，当前由 `PendingToolScreen` 明确标注状态 |

## 已接通的「假数据 → 真实数据源」（计划 §2.5 抽样）

| 设计稿演示值 | 现在的来源 |
|---|---|
| `13 款精选工具` / 分类 `4/2/2/4` | `ToolCatalog.totalCount` / `countOf(category)` 动态计算 |
| `98.4 Bits`、`84 个独立字符`、`3.2 亿年` | `PasswordEngine.entropyBits / poolSize / crackEstimate` |
| `1714567890.428`、`2024-05-01 12:51:30` | `System.currentTimeMillis()` + `java.time` |
| `3.2808`、`3.28084` | `BigDecimal` 实时换算 |
| `d41d8cd9…` / `e3b0c442…` | `MessageDigest` 对当前输入实时摘要 |
| `42 次极速唤醒`、`42 条日志`、`1.68 MB` | Room `tool_usage_stats` 求和 / 历史表计数 / 真实扫描缓存与 DB 文件 |
| `4/4 已激活`、`已按需授权` | 注册表分类数 / `ContextCompat.checkSelfPermission` |
| `OmniBox v1.0.0-release` | `BuildConfig.VERSION_DISPLAY` |

## 已知偏差（有意为之，均已在代码注释或本文件登记）

1. **分类计数**：设计稿 Chip 写「媒体图像 (2)」，但按 13 款工具的真实归类应为媒体图像 3、传感器硬件 2；界面显示真实计数 3/2。计划 §2.5 要求把演示值换成真实值，故不照抄。
2. **`<h1>` 模板残留**：`unit_converter` 标题 "Unit Converter Detail"、`_3` 标题 "Base64 Encoder Detail"、`dev_tools` 标题 "Network Ping Diagnostic" 与实际内容不符，按实际内容命名为「单位换算」「时间戳转换与计算」「代码与格式化」。
3. **Roboto Flex 字体文件缺失**：设计包不含字体二进制，`Type.kt` 暂用 `FontFamily.Default`，字阶数值已按 `DESIGN.md` 落地；放入 `roboto_flex.ttf` 后只需替换 `OmniFontFamily`。
4. **语音输入按钮**：设计稿首页/搜索页有 `mic` 图标，P1 未接入本地语音识别，按钮存在但不伪装可用（不弹假结果）。
5. **离线汇率**：`UnitEngine` 用内置快照值，界面已标注「不联网更新，仅作估算参考」，符合零网络权限约束。
6. **Room schema**：`exportSchema = false`，P2 接入 schema 归档后再开启。
7. **工程位于中文路径**：`软件开发/OmniBox` 触发 AGP 8.7 的非 ASCII 路径检查，已在 `gradle.properties` 用 `android.overridePathCheck=true` 放行（AGP 标记 experimental）。本机 aapt2 / apksigner / D8 在中文路径下均工作正常；若后续 NDK/FFmpeg 出现路径编码问题，改用 ASCII 目录联接指向本工程。`gradle-wrapper.jar` 已随工程提供，不再是缺口。
8. **Material Symbols 图标为语义近似映射**：设计稿使用 100+ 个 Material Symbols 名称，P1 在 `OmniIcon.kt` 里建立 149 个键的映射，复用一组已验证存在的 Compose 矢量图标（避免首次编译因某个图标名不存在而整体失败）。P2 接入完整 Material Symbols 资源后，只需改这一个文件即可换成精确图标。

## 首次真实编译的修复记录（静态自检查不出的问题）

| # | 报错 | 修法 |
|---|---|---|
| 1 | AGP 配置阶段 `Your project path contains non-ASCII characters` | `gradle.properties` 加 `android.overridePathCheck=true` |
| 2 | `app/build.gradle.kts:80 Unresolved reference: ndk` | `ndk{}` 从 `android{}` 顶层移入 `defaultConfig{}` |
| 3 | `Components.kt Unresolved reference: ToolDefinition` | `core:designsystem` 补 `implementation(project(":core:model"))` |
| 4 | `Could not find androidx.compose.ui:ui-test-manifest:.` | `core:data`（`compose=false` 且无 Compose BOM）移除两行 Compose 测试依赖 |
| 5 | `ShellScreens.kt` / `OmniBoxRoot.kt` 的 `Unresolved reference: SEARCH_RESULT` | `Routes` 补 `SEARCH_RESULT` 常量（无参搜索页入口） |

编译结果：`:app:assembleDebug` BUILD SUCCESSFUL（406 tasks），产出 `app-debug.apk` 约 18.2 MB；
`apksigner` v2 校验通过（CN=Android Debug），`minSdk 26 / targetSdk 35`，
merged manifest **零网络权限**，SHA-256 `8abc915cf0fa17557690bef3c08cdb1b7fc758dfaf244cdef3af8e15f17c5f06`。

## 下一步（按优先级）

1. ~~首次 Sync / 编译验证~~ ✅ 已完成（2026-11-10，debug APK 已产出并通过签名与权限门禁）。
2. ~~静态自检与零网络权限门禁~~ ✅ 已完成（`check-project.py` 0 错误 0 警告；两个 Gradle 门禁通过）。
3. P2：接入 `compose.md` 扫码模块与 `oom_2.md` 防 OOM 压缩管道，补权限渐进式申请与降级页。
4. 按 `ScreenId` 逐屏做 `screen.png` 截图比对（计划 §12 双门禁）。
5. 补 32 屏截图比对报告与功能矩阵验收表，然后打 release 包。
