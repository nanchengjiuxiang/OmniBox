# 万能工具箱 (OmniBox) Android 应用开发计划

| 项目 | 内容 |
|---|---|
| 文档版本 | v1.0 |
| 编制日期 | 2026-11-10 |
| 项目类型 | Android 原生应用（Jetpack Compose / Kotlin） |
| 设计基线 | `E:\Project\Workbuddy\workbuddy\软件开发\stitch_\stitch_\`（Google Stitch 第二版导出） |
| 基线资产 | 33 个 `code.html` + 32 张 `screen.png` + `tonal_utility/DESIGN.md` + `_16.md` + `compose.md` + `oom_2.md` + `jetpack_compose.txt` |
| 文档目的 | 把设计包**一比一还原为可运行的 Android 应用**，并保证每个界面元素背后都有真实的功能实现 |

---

## 0. 一页速览

- **要做什么**：一款纯本地、离线优先、零网络权限、隐私友好的 Android 工具箱，Material 3（Tonal Utility）紫色调，13 款工具，33 个界面状态。
- **不可协商的两条**：① 界面**完全按文件夹中的设计稿**（布局、控件、文案、图标、配色、层级），不重构、不合并、不删减；② 界面上出现的**每一个数值、开关、按钮、图表都要有对应的后端计算或数据**，不允许留下"好看但点不动/假数据"的控件。
- **交付形态**：可分模块编译的 Android 工程 + 33 屏截图比对报告 + 功能对照验收表 + 可上架的 release 包。
- **周期建议**：按 1 名熟练 Android 工程师估算，完整交付 **8 周**；设计包 `_16.md` 中的"3 周计划"只能覆盖 MVP 子集，见 §11.2。
- **现有可复用资产**：`jetpack_compose.txt`（15 个 Kotlin 代码块 / 10 个模块）+ `compose.md`（扫码 5 个文件）+ `oom_2.md`（图片压缩 5 个文件），共 **25 个代码块**，可覆盖约 60% 的领域层骨架，详见 §10。

---

## 1. 项目定义与不可协商约束

### 1.1 产品定义

面向开发者、极客与日常用户的**本地工具集合**：秒开、无启动页广告、无网络请求、无云端上传、无动态代码下发。所有计算在设备端完成，用户数据只留在本地沙盒。

### 1.2 五条硬约束（验收时可逐条判定）

| 编号 | 约束 | 判定方式 |
|---|---|---|
| C1 | **Android 原生应用**，不是 Web App / Hybrid / WebView 套壳 | 产物为 `.apk`/`.aab`；`adb shell dumpsys` 可查 Activity；无 WebView 承载业务页 |
| C2 | **界面与 `code.html` + `screen.png` 一致** | 33 屏逐屏截图比对，控件种类、位置、文案、配色、状态一致（§12.1） |
| C3 | **前后端功能匹配** | §5 矩阵中每一行的"可见元素"都有对应 UseCase；无死按钮、无写死假数据（§5.1） |
| C4 | **零 INTERNET 权限 / 全功能离线可用** | `AndroidManifest.xml` 不含 `android.permission.INTERNET`；飞行模式下 33 屏全部可用 |
| C5 | **Material 3 规范 + 深浅色双主题** | 色彩/字阶/圆角/组件尺寸与 `DESIGN.md` 一致；系统切换深色后 33 屏无对比度失效 |

### 1.3 完成定义（Definition of Done）

1. 33 个界面状态在真机上逐一复现并通过截图比对；
2. §5 的 13 款工具与 6 类全局能力全部有真实实现，输入 → 计算 → 输出 → 持久化链路闭合；
3. 飞行模式下全功能可用，静态扫描确认无网络调用、无第三方 SDK 回传；
4. 权限拒绝、传感器缺失、超大图片、低内存等降级路径可用；
5. release 包通过冷启动、内存峰值、OOM 压测与商店权限审查清单。

---

## 2. 设计资产基线（UI 唯一事实来源）

### 2.1 权威目录

```
软件开发\stitch_\stitch_\        ← 唯一权威版本（70 文件 / 9.43 MB）
├─ _1 … _15, _17, _18\           ← 17 个编号屏目录（各 code.html + screen.png）
├─ 16 个语义屏目录\              ← dev_tools / exif_1 / exif_2 / favorites / format_converter /
│                                  home_dashboard / omnibox_offline_utility_suite / oom_1 /
│                                  password_generator / settings / sound_meter / text_hash /
│                                  tool_history / tool_settings / unit_converter / zxing
├─ tonal_utility\DESIGN.md       ← 设计系统 token
├─ _16.md                        ← PRD / 架构 / 模块拓扑 / 3 周计划
├─ compose.md                    ← 扫码模块 Kotlin 源码（5 文件 / 879 行）
├─ oom_2.md                      ← 图片压缩防 OOM Kotlin 源码（5 文件 / 1227 行）
└─ jetpack_compose.txt           ← 全模块 Kotlin 源码（15 代码块 / 1826 行 / 10 模块）
```

> `软件开发\万能工具箱\stitch_\` 是**旧版导出**（63 文件），33 屏正文与新版 100% 相同但编号被重排，**禁止作为开发依据**；建议归档，不要在工程中引用。

### 2.2 三源分工

| 来源 | 职责 | 使用规则 |
|---|---|---|
| `code.html` | **结构事实源**：DOM 层级、控件类型、文案、图标名、Tailwind 类名（含色值/圆角/间距） | 逐屏照抄为 Compose 组件树；类名即 token 名 |
| `screen.png` | **视觉事实源**：最终像素效果、留白、字重、图标填充态、阴影层级 | 作为截图比对基准；宽度 315–706px 不一，比对时按内容区域对齐 |
| `DESIGN.md` | **token 事实源**：色板、字阶、8dp 栅格、断点、组件尺寸 | 生成 `Color.kt` / `Type.kt` / `Shape.kt` / `Dimens.kt` |
| `_16.md` 等 4 份文档 | **工程与领域事实源**：模块划分、MVI、ToolRegistry、Kotlin 实现 | 直接复用代码，见 §10 |

### 2.3 资产已知缺陷与处置

| # | 问题 | 影响 | 处置 |
|---|---|---|---|
| A1 | 外层多套一层 `stitch_\` 目录 | 路径易错 | 开发前把内层内容上提一级，或直接以 `stitch_\stitch_\` 为根引用；不改动文件 |
| A2 | 17 个编号目录在新旧版间被整体洗牌（`_2`↔`_16`、`exif_1`↔`exif_2` 等） | 按目录名沟通会串页 | **一律按页面内容/附录 A 的页面 ID 沟通**，禁止用 `_N` 作为需求编号 |
| A3 | 部分 `<h1>` 与内容不符（`_10` 标题写 Network Ping Diagnostic，实为转盘；`_8` 实为指南针） | 易误实现网络诊断功能 | 以正文与截图判定页面用途；**设计稿中不存在网络诊断功能，不得实现** |
| A4 | 主色冲突：正文写 `#6750A4`，front matter 与 HTML `tailwind.config` 用 `#4F378A` | 色彩不一致 | **裁定以 HTML 的 `tailwind.config` + front matter 为准**（满足 C2），`DESIGN.md` 正文视为笔误并修订 |
| A5 | 首页两版：13 款（`home_dashboard`）与 12 款（`omnibox_offline_utility_suite`） | 工具数量口径不一 | **以 13 款版为准**；12 款版判定为旧副本，不实现、不进导航（该目录也是唯一缺截图的屏） |
| A6 | `code.html` 依赖 Tailwind CDN + Google Fonts | 原型离线打不开 | Android 端全部改为本地 Compose 组件与打包字体资源，不引入任何 CDN |
| A7 | 截图宽度不一（315–706px，高多为 1600px；一张 585×1960） | 比对困难 | 比对脚本按内容区域裁剪后做结构比对，不按整图尺寸硬比 |
| A8 | 界面内大量写死演示数值（42 条记录、98.4 Bits、342°、85.3% 等） | 易被当成静态文案照抄 | 见 §2.5，全部替换为真实数据源 |

### 2.4 界面还原红线

1. **不改信息架构**：33 个屏状态一一对应 33 个 Compose 目的地（含空状态、权限态、降级态、进度态、报告态），不合并、不拆页、不改跳转层级。
2. **不改控件集合**：设计稿里有的按钮、Chip、Tab、滑块、开关、卡片、图表，一个不少；设计稿里没有的，一个不加。
3. **不改文案**：中文标题、副标题、提示语、单位、图标名（`material-symbols-outlined` 的 ligature 名）原样保留；允许补充 `contentDescription`。
4. **不改视觉 token**：`tailwind.config` 中的颜色/圆角/间距映射为 Compose token，不得自行调色或改圆角。
5. **状态栏与底部导航**：设计稿的 `09:41` / 电量图标为占位，实现用系统状态栏 + 沉浸式边缘；底部 3 Tab（首页 / 收藏 / 设置）与 80dp 高度、胶囊指示器照 `DESIGN.md §组件 6` 实现。
6. **仅允许三类替换**：① 演示数值 → 真实计算值；② 占位时间/电量 → 系统状态栏；③ 原型内联 JS → 真实 ViewModel/UseCase。
7. **深色模式**：每个 token 按 `DESIGN.md` 的 Dark Theme Equivalents 配对，禁止只做浅色。
8. **还原判定**：以 `screen.png` 为准；若 `code.html` 与截图冲突，以截图为准并记录偏差。

### 2.5 演示数据 → 真实数据源替换表（C3 的关键）

| 设计稿中出现的写死值 | 所在屏 | 必须替换为 |
|---|---|---|
| `13 款精选工具就绪` / 分类计数 (4/2/2/4) | 首页 | `ToolRegistry.getAll().size` 与按 `ToolCategory` 分组统计 |
| `6 款核心工具` / `本周启动频次 42 次` | 收藏页 | `favorite_tool` 表行数、`tool_usage` 表近 7 天聚合 |
| `98.4 Bits 极强` / 密码串 / 读音助记 | 密码生成器 | 熵值实时计算 + `SecureRandom` 输出 + NATO 音标映射 |
| `342° NNW` / `48.6 μT` / `+1.2° / -2.4°` | 指南针 | `SensorManager` 旋转矩阵解算、磁场强度、加速度计倾角 |
| `58.4 dBA` / `Min 34.2 / Avg 52.1 / Max 86.5` / 10 秒波形 | 分贝计 | `AudioRecord` RMS 实时计算 + 环形缓冲 + 60FPS 波形 |
| `减小 90.5%` / `峰值 < 16MB` / 前后对比滑块 | 图片压缩 | 编码前后 `File.length` 差值 + `Debug.getNativeHeapAllocatedSize()` + 双 Bitmap 分屏绘制 |
| `42 个 EXIF 标签` / `4 项高危` | EXIF 脱敏 | `ExifInterface` 实读标签数与敏感标签命中数 |
| `72%` / `2/3` / `02.48s` / `18.6 MB/s` / `体积缩减 85.3%` | 转码进度与报告 | 前台服务真实进度、`SystemClock` 耗时、字节吞吐、产物体积统计 |
| `28 条扫码历史` / 分类计数 (14/3/9/2) | 扫码历史 | `scan_record` 表实查与类型分组 |
| `48 天 14 时 22 分 10 秒` / `进度 72%` / `全部 (8)` | 倒计时 | 目标时间与 `System.currentTimeMillis()` 实时差值 + Room 记录数 |
| `5 项候选` / `100% 权重闭合` / 抽取结果"火锅" | 转盘 | `wheel_preset` 表 + 权重归一化 + 加权随机抽样结果 |
| `1714567890.428` / `2024-05-01 12:51:30.428 UTC+8` | 时间戳 | 系统时钟与用户输入双向换算 |
| `42 条日志` / `320 KB` / `0 B/s` | 工具历史 | Room 记录数、DB 文件大小、网络流量恒 0（静态常量） |
| `OmniBox v1.0.0-release` / `OmniEngine v2.4` | 设置 / 工具设置 | `BuildConfig.VERSION_NAME` 与引擎版本常量 |
| `找到 3 个相关工具 · 2 条历史记录 · 2.4ms` | 搜索 | 本地索引命中数 + 实测耗时 |
| `零网络权限` / `0 B/s` 等承诺型文案 | 全局 | 属于**产品事实**，由 `AndroidManifest` 与运行期行为保证，非展示数据 |

---

## 3. 设计系统落地规范

### 3.1 色彩（以 `tailwind.config` / `DESIGN.md` front matter 为基准）

| 角色 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| primary | `#4F378A` | `#D0BCFF` | 关键操作、激活态、焦点环 |
| primary-container | `#6750A4` | `#4F378B` | 高优工具模块、选中 Chip |
| on-primary-container | `#E0D2FF` | `#EADDFF` | 容器上文字 |
| secondary-container | `#E8DEF9` | — | 底部导航胶囊指示器、类别标签 |
| tertiary-container | `#7D5260` | — | 告警徽标、传感器/电量提示 |
| surface | `#FDF8FF` | `#1D1B20` | 画布底 |
| surface-container-low | `#F7F1FC` | `#1D1B20` | 搜索/密集列表背景 |
| surface-container | `#F1ECF7` | `#211F26` | 标准卡片底 |
| surface-container-high | `#ECE6F1` | `#2B2930` | 底部栏、BottomSheet |
| surface-container-highest | `#E6E0EB` | `#36343B` | 输入轨道、分隔线 |
| on-surface / on-surface-variant | `#1C1B22` / `#494551` | `#F4EFF9` / `#CAC4D0` | 主/次文字 |
| outline / outline-variant | `#7A7582` / `#CBC4D2` | `#938F99` / `#49454F` | 边框、未选开关 |
| error / error-container | `#BA1A1A` / `#FFDAD6` | — / `#8C1D18` | 破坏性操作、诊断失败 |

**Tonal Elevation**：M3 不用方向性阴影表达层级，改用更高一级的 surface-container；Level 1–3 的 ambient 阴影按 `DESIGN.md §Elevation` 的 rgba 数值实现。

### 3.2 字体与字阶

`Roboto Flex`（打包进 `res/font`，不可在线下载），平台兜底 `Google Sans` / `Roboto`。字阶按 front matter 全量落地：`display-lg 57/64`、`headline-lg 32/40`、`title-lg 22/28`、`title-md 16/24`、`body-lg 16/24`、`body-md 14/20`、`body-sm 12/16`、`label-lg 14/20`、`label-md 12/16`、`label-sm 11/16`，字重只用 400 / 500。数字读数（哈希、dB、角度、时间戳）使用等宽变体。

### 3.3 布局与形状

- 8dp 栅格；`margin` 16dp / tablet 24dp / desktop 32dp；`gutter` 16dp / 24dp。
- 断点：<600dp 单列或 2 列；600–840dp 4–8 列 + 双窗格；>840dp 最多 12 列、最大宽 1200dp，底部导航自动停靠为左侧 Navigation Rail。
- 圆角：xs 4 / small 8 / medium 12 / large 16 / extra-large 28 / full 9999dp。
- 组件高度：搜索栏 56、按钮 40、Chip 32、底部导航 80、文本字段 56、Checkbox 18×18、Switch 52×32。

### 3.4 组件规格（照 DESIGN.md §Components 逐项实现为 Compose 组件）

| 组件 | 关键规格 |
|---|---|
| `OmniSearchBar` | 高 56、圆角 28；非激活 `#ECE6F0`，激活 `#FFFFFF`；前导搜索图标、尾随离线/语音徽标 |
| `OmniButton` | Filled / Tonal / Outlined 三型，高 40、全圆角，label-lg |
| `OmniFilterChip` | 高 32；未选描边 `#CBC4D0` 透明底，选中填充 secondary-container 并带勾选图标 |
| `OmniToolCard` | Filled / Elevated / Outlined 三型，圆角 16，对应 Level 1–2 elevation |
| `OmniTextField` | Filled（下划指示线）/ Outlined（浮动标签缺口）两型，高 56 |
| `OmniBottomNav` | 高 80、无描边；激活项 64×32 胶囊 `#E8DEF9`；label-md |
| `OmniSwitch` / `OmniCheckbox` | 52×32 轨道（按压时 thumb 16→24）/ 18×18、4dp 圆角 |

### 3.5 图标

全部使用设计稿的 **Material Symbols Outlined** ligature 名（如 `qr_code_scanner`、`offline_bolt`、`verified_user`、`graphic_eq`、`straighten`），通过打包的图标字体或矢量资源映射；`FILL 1` 的实心图标按设计稿保留，不得统一替换成线性图标。

---

## 4. 信息架构与页面清单（33 屏）

### 4.1 导航结构

```
AppNavHost
├─ MainScaffold（底部 3 Tab）
│  ├─ HomeTab        → 首页 / 搜索结果 / 工具执行工作区
│  ├─ FavoritesTab   → 收藏与快捷
│  └─ SettingsTab    → 设置 / 工具设置 / 工具历史
└─ 工具路由 tool/{tool_id}（ToolRegistry 动态注册）
   ├─ 各工具主屏 + 空状态 + 权限态 + 降级态 + 编辑态 + 进度态 + 报告态
   └─ 深链 omnibox://tool/{tool_id}，支持桌面小组件直达
```

### 4.2 页面主清单（分组）

> 「后端依赖」列即该屏必须接通的领域能力编号，详见 §5。目录名只用于定位资产，页面 ID 才是需求编号。

#### A. 主框架（7 屏）

| 目录 | 页面 ID | 用途 | 关键 UI 元素 | 后端依赖 | 数据 |
|---|---|---|---|---|---|
| `home_dashboard` | `home` | 首页 13 款工具、4 分类筛选、常用推荐 | 搜索栏、分类 Chip(4)、工具卡、离线徽标 | G1 搜索 / G2 注册表 / G3 频次 | `ToolRegistry`、Room |
| `omnibox_offline_utility_suite` | — | 首页 12 款旧副本 | 同上 | **不实现**，仅存档 | — |
| `favorites` | `favorites` | 置顶 6 款、拖拽排序、小组件就绪 | 置顶卡、管理、频次统计 | G4 收藏 / G6 小组件 | `favorite_tool`、`tool_usage` |
| `settings` | `settings` | 版本/离线承诺/模块启停/权限审计 | 版本卡、模块开关、存储、审计项 | G5 偏好 / G7 权限审计 | DataStore、PackageManager |
| `tool_settings` | `tool_settings` | 默认输出格式、精度、缓存生命周期 | WebP/JPEG/PNG 单选、16MB 守卫、缓存开关 | G5 偏好 / T5 引擎配置 | DataStore |
| `tool_history` | `tool_history` | 42 条日志、批量管理、一键粉碎、导出 | 分类 Chip、日志列表、统计条 | G8 历史 / G9 导出 | `tool_history` |
| `_9` | `search_result` | 搜索"图片"→3 工具 + 2 历史、2.4ms | 搜索栏、结果卡、历史项、收藏/删除 | G1 搜索 / G8 历史 | 本地索引 + Room |

#### B. 扫码与二维码（5 屏）

| 目录 | 页面 ID | 用途 | 关键 UI 元素 | 后端依赖 | 权限 |
|---|---|---|---|---|---|
| `_4` | `qr_scan_active` | CameraX 60fps 取景、补光灯、变焦 | 预览框、闪光灯、焦距、双 Tab | T1 扫码识读 | `CAMERA` |
| `_5` | `qr_permission` | 相机未授权的教育与降级页 | 权限说明、相册解析、生码入口 | T1 降级 | `CAMERA`（可拒） |
| `zxing` | `qr_generate` | 编码内容、外观/容错调优、导出格式与分辨率 | 实时画布、ECC 等级、Logo、SVG/1024px、保存相册 | T2 生码 | 无（写媒体库用 MediaStore） |
| `_11` | `qr_export_config` | 容错率详细配置（L/M/Q/H）与 Logo 提示 | 4 档单选、提示卡、关闭 | T2 生码 | 无 |
| `_2` | `qr_history` | 28 条本地识读历史，按类型筛选、批量清空 | 分类 Chip、批量管理、搜索、导出 | T1 / G8 | 无 |

> 说明：按内容判定，`_13` 属于文本工具空状态页，已归入 D 组。

#### C. 媒体图像（6 屏）

| 目录 | 页面 ID | 用途 | 关键 UI 元素 | 后端依赖 |
|---|---|---|---|---|
| `oom_1` | `image_compress` | 图片无损压缩、前后画质拖拽对比 | 对比滑块、原始/目标文件卡、体积与画质指标 | T5 压缩引擎 |
| `exif_2` | `exif_risk_report` | 检出 3 项高度敏感隐私（GPS/序列号/时间） | 风险横幅、原图信息、EXIF 诊断清单、更换图片 | T6 EXIF |
| `exif_1` | `exif_clean_config` | 42 标签 / 4 高危 / 12 安全，脱敏导出规则 | 敏感项列表、安全参数、一键脱敏导出 | T6 EXIF |
| `format_converter` | `format_converter` | HEIC→WebP、单/多文件、图片/音频/文档/视频四 Tab | 源文件卡、目标格式、转码策略、内存 <24MB | T7 转码 |
| `_12` | `transcoding_progress` | 前台服务转码中、72%、2/3、Wakelock、通知预览 | 环形进度、阶段、队列、通知卡 | T7 / G10 前台服务 |
| `_7` | `conversion_report` | 转换成功报告、体积缩减 85.3%、产物清单 | 指标卡、节省总览、文件清单、保存到媒体库 | T7 / G8 |

#### D. 文本与开发者（3 屏）

| 目录 | 页面 ID | 用途 | 关键 UI 元素 | 后端依赖 |
|---|---|---|---|---|
| `text_hash` | `text_tools` | MD5/SHA、Base64、URL、大小写/全半角清洗 | 4 Tab、源文本输入、实时统计、结果卡 | T8 文本引擎 |
| `_13` | `text_tools_empty` | 空状态：尚未输入文本、预设样例、清洗模式 | 空态插画、粘贴、加载示例 | T8 状态机 |
| `dev_tools` | `dev_tools` | JSON 美化/校验、进制、正则、URL、Unicode | 5 Tab、输入框、2 空格缩进、复制 | T9 开发者箱 |

#### E. 传感器硬件（4 屏）

| 目录 | 页面 ID | 用途 | 关键 UI 元素 | 后端依赖 | 权限 |
|---|---|---|---|---|---|
| `_8` | `compass_active` | 342° 罗盘、X/Y 倾角、48.6μT、校准提示、水平仪 | 罗盘盘面、双轴倾角、磁场强度、归零 | T10 传感器融合 | 无（传感器免权限） |
| `_15` | `compass_degraded` | 无地磁计降级：太阳方位角 + GPS 航向推演，±3.5° | 日晷盘、传感器矩阵状态、锁定 042° | T10 降级 | 位置权限**可选**（§8.3） |
| `sound_meter` | `sound_meter` | 实时 58.4dBA、Min/Avg/Max、10 秒波形、WHO 对照 | 仪表、折线图、暂停/重置、对照表 | T11 音频 RMS | `RECORD_AUDIO` |
| `_6` | `sound_permission` | 麦克风权限教育与模拟演示 | 权限说明、允许、隐私白皮书、模拟演示 | T11 降级 | `RECORD_AUDIO` |

#### F. 日常效率与倒计时（8 屏）

| 目录 | 页面 ID | 用途 | 关键 UI 元素 | 后端依赖 |
|---|---|---|---|---|
| `password_generator` | `password` | 强随机密码、98.4 Bits、掩码、长度、读音助记 | 结果卡、字符集开关、复制、重新生成 | T3 密码学 |
| `unit_converter` | `unit_converter` | 长度/重量/温度/面积/体积/存储/离线汇率 | 7 类 Chip、来源/目标单位、交换、速查表 | T4 换算 |
| `_3` | `timestamp` | 10/13 位时间戳、时区、双向转换、时间差推演 | 实时时钟卡、复制、日期选择、跨度计算 | T12 时间 |
| `_10` | `random_wheel` | 幸运转盘 / 抛硬币 / 骰子 / 随机数，GO 抽取 | 圆盘、音效触感、结果揭晓、权重配置入口 | T13 随机决策 |
| `_18` | `wheel_editor` | 转盘编辑：模板、5 项候选、100% 权重、拖拽排序 | 场景模板、权重总览、候选列表、均分 | T13 配置 |
| `_1` | `countdown_empty` | 倒计时空状态、灵感模板推荐 | 0 天、新建第一个、模板卡 | T14 倒计时 |
| `_17` | `countdown_list` | 置顶目标、48天14时22分10秒、进度 72%、8 条记录 | 目标卡、分类 Chip、纪念日列表、新建 | T14 / G6 |
| `_14` | `countdown_editor` | 新建目标事件：名称、类别、公历/农历、快捷预设 | 表单、类别标签、日期选择、+30/+100 天 | T14 写入 |

> 上表合计 33 个屏目录（含 1 个不实现的旧首页副本）；实际需实现 **32 个界面状态**。

### 4.3 路由表（与 `_16.md` 对齐）

| 路由 | 工具 ID | 分类 |
|---|---|---|
| `tool/qrcode` | `qrcode` | 日常生活 |
| `tool/unit_converter` | `unit_converter` | 日常生活 |
| `tool/random_wheel` | `random_wheel` | 日常生活 |
| `tool/password` | `password` | 日常生活 |
| `tool/timestamp` | `timestamp` | 开发调试 |
| `tool/text_tools` | `text_tools` | 开发调试 |
| `tool/dev_tools` | `dev_tools` | 开发调试 |
| `tool/countdown` | `countdown` | 日常生活 |
| `tool/image_compress` | `image_compress` | 媒体图像 |
| `tool/format_converter` | `format_converter` | 媒体图像 |
| `tool/exif` | `exif` | 媒体图像 |
| `tool/compass` | `compass` | 传感器硬件 |
| `tool/decibel` | `decibel` | 传感器硬件 |

> 首页按 13 款统计；分类 Chip 显示 4/2/2/4 与设计稿不一致（2 个媒体图像工具），**实现时以 `ToolRegistry` 的实际分组为准并同步修正 Chip 计数文案**，这是 C2 与 C3 冲突时唯一允许的数值修正，需在验收记录中登记。

### 4.4 统一状态机

每屏的 `UiState` 至少覆盖 6 态，且每态都有对应设计稿：

`Loading` → `Empty`（`_1` / `_13` / `text_tools_empty`）→ `Success`（主屏）→ `Error`（Snackbar + 重试）→ `PermissionRequired`（`_5` / `_6`）→ `Degraded`（`_15`）。

`UiEffect` 统一经 `Channel` 驱动 Snackbar、导航、剪贴板、触觉反馈、分享。

---

## 5. 前后端功能匹配矩阵（核心章节）

### 5.1 匹配原则

1. **字段可追溯**：界面上每一个数值/文本标签都在 §2.5 有数据源，代码中禁止出现 `98.4`、`42`、`342°` 等硬编码展示常量。
2. **控件可响应**：每个可点击控件绑定一个 `UiEvent`；纯展示标签除外。`onClick = {}` 视为缺陷。
3. **文案承诺可兑现**：设计稿中"零 OOM 防御""NDK/SIMD""纯本地 CSPRNG"等承诺，必须有对应实现（§5.4），否则修改文案。
4. **权限与功能同源**：使用相机/麦克风/传感器的界面，必须与权限态、降级态成对实现。
5. **数据闭环**：工具结果可复制、可保存媒体库、可写入历史；历史可查询、批量删除、导出。

### 5.2 十三款工具逐项规格

#### T1 二维码识读（`qrcode`）

| 项 | 内容 |
|---|---|
| UI 依据 | `_4`（活跃态）、`_5`（权限态）、`_2`（历史）、`zxing`/`_11`（生成），双 Tab「扫码识别 / 离线生成」 |
| 前端可见元素 | 取景器、闪光灯、变焦 1x、识别结果、历史入口、设置入口、类型筛选 Chip |
| 后端实现 | `CameraX` `Preview` + `ImageAnalysis`（60fps 目标，按设备能力降级）；`ML Kit Barcode Scanning`（本地模型，**打包模型、不联网**）或 `ZXing MultiFormatReader`；相册图片走 `RGBLuminanceSource` + `HybridBinarizer` 静态解析 |
| 权限 | `CAMERA`（仅取景时申请）；相册解析用 Photo Picker，**无存储权限** |
| 持久化 | `scan_record(id, content, format, type, timestamp, is_favorite)`；类型枚举 URL / WIFI / TEXT / CONTACT 与筛选项对应 |
| 输出 | 复制到剪贴板、打开系统浏览器/拨号（由用户确认）、批量导出为本地文件 |
| 验收 | 飞行模式下扫码成功；拒权后相册解析仍可用；28 条假数据替换为实查结果 |

#### T2 二维码生成（`qrcode/generate`）

| 项 | 内容 |
|---|---|
| UI 依据 | `zxing`（编码内容 + 外观容错 + 导出格式/分辨率）、`_11`（ECC L/M/Q/H 详配） |
| 前端可见元素 | 实时渲染画布、ECC 四档、中心 Logo 开关、SVG / 1024×1024、Level H(30%)、UTF-8、保存到相册 |
| 后端实现 | `ZXing 3.5.3` `QRCodeWriter` + `BitMatrix` → `Bitmap`；`ErrorCorrectionLevel` 映射 L 7% / M 15% / Q 25% / H 30%；Logo 叠加后校验仍可解码；SVG 由 BitMatrix 生成 path 字符串 |
| 依赖 | `com.google.zxing:core`（纯 Java、离线） |
| 输出 | `MediaStore.Images.Media.insertImage` 或 Scoped Storage `MediaStore` 写入；SVG 以文件分享 |
| 验收 | 改变任一档 ECC/内容，画布**毫秒级重绘**；H+Logo 仍可被 T1 识读 |

#### T3 安全密码生成器（`password`）

| 项 | 内容 |
|---|---|
| UI 依据 | `password_generator` |
| 前端可见元素 | 结果 Token、掩码切换、长度 +/−、字符集开关、熵值 Bits 与强度徽标、读音助记、重新生成、复制、历史 |
| 后端实现 | `java.security.SecureRandom`（ backed by `/dev/urandom` / Android Keystore 熵源）；字符集 = 大写/小写/数字/符号的并集；熵 `length × log2(charsetSize)` 实时计算并映射「极强/强/中/弱」；读音助记用 NATO 音标表映射 |
| **禁止** | 禁止 `Math.random()`、禁止把生成结果写入日志或网络、禁止持久化明文密码 |
| 持久化 | 仅记录「生成时间 + 长度 + 强度」到 `tool_history`，**不存密码本体** |
| 验收 | 连续生成 1000 次无重复；熵值随长度/字符集变化；飞行模式可用 |

#### T4 单位换算（`unit_converter`）

| 项 | 内容 |
|---|---|
| UI 依据 | `unit_converter` |
| 前端可见元素 | 7 类 Chip（长度/重量/温度/面积/体积/数据存储/离线汇率）、来源与目标单位下拉、交换按钮、常用基准速查表、复制 |
| 后端实现 | 离线换算表 + `BigDecimal`（避免浮点误差）；温度与汇率走仿射/查表而非线性比例；「离线汇率」使用**打包的静态快照**并在界面标注快照日期（因 C4 禁止联网） |
| 数据 | 单位定义打包为 assets JSON / Kotlin object，随版本更新 |
| 验收 | 7 类共 ≥40 个单位双向换算正确；交换按钮互换来源与目标；离线汇率显示快照日期 |

#### T5 图片无损压缩（`image_compress`）

| 项 | 内容 |
|---|---|
| UI 依据 | `oom_1` |
| 前端可见元素 | 无损画质对比滑块、原图 48MP / 12.4MB、目标 WebP 1.18MB、减小 90.5%、峰值 <16MB、格式/质量/分辨率参数、保存到相册 |
| 后端实现 | `BitmapFactory.Options.inJustDecodeBounds` → 计算 2 的幂 `inSampleSize` → `ImageDecoder`（Android 10+）分块解码 → `Bitmap.compress(WebP/JPEG/PNG)`；`Dispatchers.IO`；EXIF 保留/抹除开关；P3 广色域 ICC 保真；PSNR 由双 Bitmap 采样计算 |
| OOM 防护 | 单实例、用完 `recycle()`、严控堆峰值 ≤16MB（`StrictMode` + `Debug` 采样），超 2400 万像素启用分块 |
| 复用 | `oom_2.md` 的 `ImageCompressEngine.kt` / `CompareSplitView.kt` 直接落地 |
| 验收 | 48MP 大图不 OOM；体积与 PSNR 为实测值；对比滑块可拖动 |

#### T6 EXIF 隐私擦除（`exif`）

| 项 | 内容 |
|---|---|
| UI 依据 | `exif_2`（风险报告）、`exif_1`（深度脱敏配置） |
| 前端可见元素 | 「已检出 3 项高度敏感隐私」横幅、GPS/序列号/时间戳、42 标签 / 4 高危 / 12 安全、脱敏项勾选、一键安全脱敏导出 JPG |
| 后端实现 | `androidx.exifinterface:exifinterface` 逐标签读取；敏感标签白名单（GPSLat/Long、DateTimeOriginal、Make/Model、SerialNumber、Software、UserComment 等）分类为高危/安全；写出时删除高危标签、保留 ISO/光圈/快门等摄影参数；输出到 MediaStore 新文件（不覆盖原图） |
| 验收 | 导出文件用第三方工具校验不含 GPS；统计数字为实读值 |

#### T7 全能格式转换（`format_converter`）

| 项 | 内容 |
|---|---|
| UI 依据 | `format_converter`（配置）、`_12`（进度）、`_7`（报告） |
| 前端可见元素 | 图片/音频提取/文档数据/视频四 Tab、单文件/批量、HEIC 18.6MB 4032×3024 10-bit HDR、目标 WebP Lossy 85%、P3→sRGB、72% 进度、阶段 2/3、批量队列、前台通知预览、体积缩减 85.3%、吞吐 18.6MB/s、文件清单 |
| 后端实现 | 图片：`ImageDecoder` + `Bitmap.compress` / libwebp；HEIC/HDR→sRGB 色彩转换；音视频/文档若纳入范围则用 `FFmpeg-Kit`（**本地 NDK 编译、裁剪 so、无网络**），否则按 §5.4 收敛文案；批量任务用 `WorkManager` + 前台 `Service`（`foregroundServiceType` 依 Android 14 声明）+ `WakeLock`；进度经 `StateFlow` 推送；通知显示实时进度 |
| 内存 | 常驻 <24MB，线程池大小按 `Runtime.availableProcessors()` 限制，超限熔断 |
| 持久化 | `conversion_task(id, source_uri, target_format, status, progress, created_at)`；报告数据由实际产物统计 |
| 复用 | `jetpack_compose.txt` §3 的 `FormatConverterContract/Screen`、`TranscodingProgressScreen`、`ConversionReportScreen` |
| 验收 | 3 文件批量转码；杀掉界面后前台服务继续；报告数值与文件系统一致 |

#### T8 文本与散列工具（`text_tools`）

| 项 | 内容 |
|---|---|
| UI 依据 | `text_hash`（主屏）、`_13`（空态） |
| 前端可见元素 | MD5/SHA、Base64、URL 编解码、排版大小写 4 Tab；源文本输入、粘贴、清空；字符/行数/大小(UTF-8) 实时统计；快速清洗（去首尾空格/空行/全半角/中英排版）；结果卡 |
| 后端实现 | `MessageDigest` (MD5/SHA-1/SHA-256/SHA-512)、`java.util.Base64`、`URLEncoder`/`URLDecoder`（UTF-8）、全角↔半角映射表（0x3000 / 0xFF01–0xFF5E）、正则清洗；统计用 `String.codePointCount` 与 `toByteArray(UTF_8).size` |
| 状态 | 空输入 → `_13` 空态；预设样例与"加载示例测试数据"按钮填充内置样例 |
| 验收 | 与标准命令行 `md5sum`/`base64` 结果一致；大文本不阻塞主线程 |

#### T9 开发者全能箱（`dev_tools`）

| 项 | 内容 |
|---|---|
| UI 依据 | `dev_tools` |
| 前端可见元素 | JSON / 进制 / 正则 / URL / Unicode 五 Tab、2 空格缩进美化、复制、粘贴、压缩、高亮 |
| 后端实现 | JSON 用 `org.json` 或 `kotlinx.serialization` 解析 + 自定义 pretty printer（校验错误定位行列）；进制 2/8/10/16 互转（`BigInteger` 防溢出）；正则用 `java.util.regex` 输出匹配分组与高亮区间；Unicode `\uXXXX` ↔ 中文互转 |
| 安全 | 正则执行设超时/长度上限，防止 ReDoS 卡死 |
| 验收 | 非法 JSON 给出行列错误；超长输入不死机 |

#### T10 指南针与水平仪（`compass`）

| 项 | 内容 |
|---|---|
| UI 依据 | `_8`（活跃）、`_15`（降级） |
| 前端可见元素 | 方位角 342°、方位文字 NNW、N/E/S/W 罗盘、X/Y 双轴倾角、地磁强度 48.6μT、校准「∞」提示、归零触觉反馈、应急手电、传感器矩阵状态 |
| 后端实现 | `SensorManager` 注册 `TYPE_ACCELEROMETER` + `TYPE_MAGNETIC_FIELD`（+ `TYPE_GYROSCOPE` 融合）；`getRotationMatrix` + `getOrientation` 解算方位角；倾角由重力向量计算；强度取磁力计 `values` 模长；低通滤波 + 校准提示（磁场异常阈值）；`HapticFeedbackConstants` 归零反馈；手电用 `CameraManager.setTorchMode`（需相机权限，未授权时隐藏或提示） |
| 降级 | `getDefaultSensor(TYPE_MAGNETIC_FIELD) == null` → 切 `_15`：太阳方位角算法（本地天文公式 + 系统时间 + **用户手动输入或最后已知位置**）+ 方向推演盘 ±3.5°，禁止静默申请位置权限（§8.3） |
| 验收 | 无地磁设备/模拟器进入降级页且不崩溃；有传感器时指北正确 |

#### T11 环境分贝计（`decibel`）

| 项 | 内容 |
|---|---|
| UI 依据 | `sound_meter`（活跃）、`_6`（权限态） |
| 前端可见元素 | 实时 58.4 dBA、48kHz A 加权、0/60/85/120dB 刻度、Min/Avg/Max、近 10 秒 60FPS 折线、WHO 对照基准表、暂停采样、重置统计、模拟演示 |
| 后端实现 | `AudioRecord` 48kHz / `ENCODING_PCM_16BIT` / `CHANNEL_IN_MONO`；短缓冲读取 → RMS → `20·log10(RMS/ref)` → A 加权近似修正 → dBA；环形缓冲保存 10 秒采样用于波形；统计窗口内 min/avg/max；`Dispatchers.Default` 计算，Compose Canvas 绘制 |
| 隐私 | **音频字节只存于内存、算完即弃**；不落盘、不录音文件、不上传；生命周期结束立即 `release()` |
| 降级 | 拒绝 `RECORD_AUDIO` → `_6` 页；「体验模拟演示」用内置合成数据驱动同一 UI，且明确标注"演示" |
| 复用 | `jetpack_compose.txt` §4 `AudioDecibelEngine.kt` / `SoundMeterScreen.kt` |
| 验收 | 静音环境读数接近本底；权限拒绝可用演示；dumpsys 确认无音频文件产生 |

#### T12 时间戳推算（`timestamp`）

| 项 | 内容 |
|---|---|
| UI 依据 | `_3` |
| 前端可见元素 | 实时 10 位秒 / 13 位毫秒（`.428`）、本地/UTC+0/纽约时区、复制秒/毫秒、时间戳↔日期双向、日期选择器、时间差与跨度推演 |
| 后端实现 | `java.time` + desugaring（`Instant`/`ZonedDateTime`/`ZoneId`）；1s 刷新 `System.currentTimeMillis()`；毫秒部分保留；双向转换与差值（`Duration.between` 输出天/时/分/秒）；时区列表离线打包 |
| 验收 | 与 `date +%s` 一致；跨时区与闰秒边界不崩 |

#### T13 随机决策转盘（`random_wheel`）

| 项 | 内容 |
|---|---|
| UI 依据 | `_10`（主屏）、`_18`（编辑） |
| 前端可见元素 | 幸运转盘/抛硬币/掷骰子/随机数 4 模式、5 项候选、均匀扇区、GO 抽取、结果揭晓、音效触感、权重滑块、100% 权重总览、场景模板、长按拖拽排序、均分权重 |
| 后端实现 | Compose `Canvas` 按权重归一化绘制扇区与指针；`SecureRandom` 加权抽样（前缀和 + 二分）；旋转动画用 `Animatable` 减速到目标角度；`HapticFeedback` + 本地音效（打包资源，不联网）；预设与候选持久化到 `wheel_preset` / `wheel_item(weight, order)`；抽选结果写 `tool_history` |
| 复用 | `jetpack_compose.txt` §7 `RandomWheelScreen.kt` |
| 验收 | 权重和始终 100%（编辑时实时校验）；1000 次抽样分布近似权重 |

#### T14 倒计时与纪念日（`countdown`）

| 项 | 内容 |
|---|---|
| UI 依据 | `_1`（空态）、`_17`（列表）、`_14`（编辑） |
| 前端可见元素 | 48天14时22分10秒、筹备进度 72%、置顶目标、分类 Chip（纪念日2/倒计时4/累计天数1/生日提醒1）、公历/农历、快捷预设 +30/+100 天、灵感模板、同步小部件、草稿/保存 |
| 后端实现 | `Room` 存事件；`WorkManager` 周期刷新与到期提醒（通知）；**离线农历库**（打包 1900–2100 农历数据表，不联网）；倒计时用 `System.currentTimeMillis()` 每秒刷新（列表用 `Flow`）；进度 = 已过 / 总周期；`Glance` 桌面小组件 2×2 / 4×2 读取同一 Room 数据 |
| 复用 | `jetpack_compose.txt` §6 `CountdownEntity.kt` |
| 验收 | 公历/农历每年循环正确；杀进程后小组件仍更新；到期通知准时 |

### 5.3 全局能力（G1–G10）

| 编号 | 能力 | 覆盖界面 | 实现 |
|---|---|---|---|
| G1 | 本地搜索 | 首页、`_9` | ToolRegistry 内存索引 + Room FTS/过滤；显示命中数与实测耗时 |
| G2 | ToolRegistry | 首页/收藏/设置/路由 | `_16.md` 的 `ToolProvider` 接口 + Hilt `Multibindings` 汇总，13 款工具零硬编码入口 |
| G3 | 使用频次 | 首页常用推荐、收藏 | 每次工具启动写 `tool_usage`，按 7 天窗口聚合排序 |
| G4 | 收藏与置顶 | `favorites` | `favorite_tool` 表 + 拖拽排序 order 字段 |
| G5 | 偏好设置 | `settings`、`tool_settings` | DataStore（默认格式、质量、缓存生命周期、动态色彩、模块启停） |
| G6 | 桌面小组件 | 收藏页、倒计时 | `Glance` + `WorkManager` 刷新；深链 `omnibox://tool/{id}` 直达 |
| G7 | 权限审计 | `settings` | `PackageManager` 查询已授予权限与申请历史，展示"零 INTERNET 权限"事实 |
| G8 | 工具历史 | `tool_history`、`qr_history` | 统一 `tool_history` 表 + 分类筛选 + 批量删除 + 一键粉碎（secure delete 语义） |
| G9 | 离线导出/备份 | 历史页、报告页 | 导出到本地文件（JSON/CSV），经 `FileProvider` 分享；不上传 |
| G10 | 前台服务与通知 | `_12` | Android 14 `foregroundServiceType` 声明、WakeLock、通知渠道与进度更新 |

### 5.4 文案承诺 ↔ 实现对照表（防止"假后端"）

| 设计稿文案 | 必须有真实实现 |
|---|---|
| `纯本地离线优先 · 零网络权限` | Manifest 无 INTERNET；所有依赖为本地库；静态扫描无 `HttpURLConnection`/`OkHttp` 调用 |
| `零 OOM 防御 · 峰值常驻管控 < 16MB` | `inSampleSize` + `ImageDecoder` + 分块 + 内存采样断言 |
| `纯本地 NDK/SIMD 线程池执行` | 若保留该文案，须接入裁剪版 FFmpeg-Kit/libwebp 的 `.so` 与线程池；**否则改为"本地协程异步流水线"并登记文案变更** |
| `SecureRandom 强随机 / Android Keystore 硬件随机源` | T3、T13 使用 `SecureRandom`，禁用 `Math.random()` |
| `CameraX 60fps · ML Kit 纯离线解码` | ML Kit 模型随包分发（`bundled` 依赖），禁止 `play-services-mlkit` 的按需下载模型 |
| `本地沙盒数据库 (Room v2.6)` | Room 实体与 DAO 真实落库，数据库文件在 app 私有目录 |
| `AudioRecord 只存内存瞬时缓冲区` | T11 无文件写入，单元测试断言目录无新增文件 |
| `太阳方位角与 GPS 移动航向推演` | T10 降级算法；GPS 部分改为**可选授权**（§8.3） |
| `FFmpeg-Kit/NDK 轻量编译` | 同第三行；需在 P2 阶段做技术验证并决策保留或改文案 |

---

## 6. 工程架构

### 6.1 技术栈

| 层 | 选型 |
|---|---|
| 语言/UI | Kotlin 2.x + Jetpack Compose（Material 3）、`compileSdk 35`、`minSdk 26`（覆盖 Android 8+，动态色彩 12+ 特性做版本分支） |
| 架构 | MVI：`StateFlow<UiState>` + `Channel<UiEffect>` + `sealed UiEvent` |
| DI | Hilt |
| 本地存储 | Room（历史/收藏/倒计时/转盘/扫码/转码任务）、DataStore（偏好） |
| 后台 | WorkManager、前台 Service + WakeLock、Glance 小组件 |
| 图像 | CameraX、ZXing 3.5.3、ML Kit barcode（bundled）、ExifInterface、ImageDecoder、libwebp |
| 硬件 | SensorManager、AudioRecord、CameraManager（手电）、Vibrator/HapticFeedback |
| 加密 | `java.security.SecureRandom` / `MessageDigest` / `java.util.Base64` |
| 音视频 | FFmpeg-Kit 裁剪版（待 P2 验证）或原生 MediaCodec/ImageDecoder |
| 测试 | JUnit5、Truth、Compose UI Test、Robolectric、Macrobenchmark |
| **禁止** | 任何网络库、崩溃/统计/广告 SDK、动态代码下发、WebView 承载业务 |

### 6.2 Gradle 模块拓扑（照 `_16.md`）

```
:app                     Application、AppNavHost、Hilt 汇总、MainActivity
:core:designsystem       Color/Type/Shape/Dimens + §3.4 的 7 个基础组件
:core:common             Result<T>、Dispatcher、扩展、权限封装、OfflineClock
:core:model              ToolDefinition / ToolCategory / 公共枚举
:core:data               DataStore、Room Database、ToolRegistry、Repository 实现
:feature:home            首页 / 搜索 / 收藏 / 设置 / 历史（可合为 :feature:shell）
:feature:qrcode          T1 + T2
:feature:unitconverter   T4
:feature:randomwheel     T13
:feature:password        T3
:feature:timestamp       T12
:feature:texttools       T8 + T9
:feature:imagecompress   T5
:feature:exif            T6
:feature:formatconverter T7
:feature:compass         T10
:feature:countdown       T14
:feature:decibel         T11
```

每个 `:feature:*` 自包含 UI + Domain + Data，通过 `ToolProvider` 向 `:app` 注册路由，支持单独禁用（对应设置页"模块启停"）。

### 6.3 MVI 契约模板

```kotlin
sealed interface XxxUiState { data object Loading; data object Empty;
    data class Success(val data: XxxModel); data class Error(val cause: Throwable);
    data object PermissionRequired; data object Degraded }
sealed interface XxxUiEvent { /* 每个可见控件一个事件 */ }
sealed interface XxxUiEffect { data class ShowSnackbar(val msg: String);
    data object CopyToClipboard; data object TriggerHaptic; data class Navigate(val route: String) }
```

### 6.4 ToolRegistry

照 `_16.md §四`：

```kotlin
data class ToolDefinition(
    val id: String, val titleRes: Int, val descRes: Int,
    val category: ToolCategory, val iconRes: Int, val route: String,
    val requiredPermissions: List<String> = emptyList(),
    val supportsWidget: Boolean = false
)
interface ToolProvider {
    fun getToolDefinition(): ToolDefinition
    fun registerRoute(builder: NavGraphBuilder, controller: NavHostController)
}
```

Hilt `@IntoSet` 汇总 13 个 provider；首页、收藏、搜索、小组件、设置全部从同一注册表取数——这是保证"界面计数与功能数量一致"的技术手段。

### 6.5 深链与小组件

- `omnibox://tool/{tool_id}` → `AppNavHost` 直接导航；小组件点击直达。
- Glance 2×2 / 4×2 读取 Room 中置顶工具与倒计时，避免启动 Activity。

---

## 7. 数据模型

### 7.1 Room 表（v1）

| 表 | 关键字段 | 服务于 |
|---|---|---|
| `tool_definition_cache` | id, category, last_used_at, use_count_7d | 首页/收藏/搜索 |
| `favorite_tool` | tool_id, sort_order, pinned_at | 收藏页拖拽排序 |
| `tool_usage` | id, tool_id, timestamp | 频次统计 |
| `tool_history` | id, tool_id, action, summary, payload_ref, created_at, size_bytes | 工具历史、报告、导出 |
| `scan_record` | id, content, format, type(URL/WIFI/TEXT/CONTACT), created_at | 扫码历史与筛选 |
| `conversion_task` | id, source_uri, target_format, status, progress, stage, created_at | 转码进度与报告 |
| `countdown_event` | id, title, category, calendar_type(GREGORIAN/LUNAR), target_time, start_time, repeat_rule, pinned, widget_synced | 倒计时/纪念日 |
| `wheel_preset` | id, name, mode, created_at | 转盘模板 |
| `wheel_item` | id, preset_id, label, weight, sort_order | 权重与拖拽排序 |

> 密码本体、音频字节、相机帧**一律不入库、不落盘**。

### 7.2 DataStore 键

`theme_mode`、`dynamic_color_enabled`、`default_output_format`、`default_quality`、`resolution_scale`、`strip_gps_exif`、`keep_photo_exif`、`preserve_p3`、`heap_guard_enabled`、`cache_ttl_days`、`enabled_modules`、`haptic_enabled`、`sound_enabled`、`offline_exchange_rate_snapshot_date`。

### 7.3 文件与缓存生命周期

- 所有产物写入 app 私有目录或经 `MediaStore` 写入公共媒体库（用户可见）。
- 缓存按 `tool_settings` 的 TTL 自动清理；「一键粉碎」清除历史记录与关联缓存文件。
- 导出文件经 `FileProvider` 分享，`FLAG_GRANT_READ_URI_PERMISSION` 限时授权。

### 7.4 备份与导出

本地 JSON/CSV 导出（历史、倒计时、转盘预设）；导入时校验版本与 schema；**不提供云同步**（C4）。

---

## 8. 权限、隐私与降级

### 8.1 权限矩阵

| 权限 | 使用工具 | 申请时机 | 拒绝后 |
|---|---|---|---|
| `CAMERA` | T1 取景、T10 手电 | 首次点击「扫码识别」 | `_5` 页：相册解析 / 生码 |
| `RECORD_AUDIO` | T11 分贝计 | 首次点击「开始监测」 | `_6` 页：模拟演示 |
| `ACCESS_FINE_LOCATION` | T10 降级态 GPS 航向（**可选**） | 用户在降级页显式开启 | 太阳方位角 + 手动方位 |
| `POST_NOTIFICATIONS` | 转码进度、倒计时提醒 | Android 13+ 首次需要通知时 | 应用内进度仍可见 |
| `VIBRATE` | 转盘、归零反馈 | 安装时 | 静默降级 |
| **INTERNET** | **不申请** | — | — |
| 存储 | 无 | 用 Photo Picker / MediaStore / Scoped Storage | — |

### 8.2 渐进式授权流程

`不启动不索权 → 用前 Rationale Dialog（说明用途 + 数据不出端）→ rememberLauncherForActivityResult(RequestMultiplePermissions) → 拒绝则进入对应降级页 → 永久拒绝提供「去系统设置」入口`。

### 8.3 设计稿与现实冲突的裁定

- 降级页 `_15` 文案写「GPS 移动速度 > 1.2 m/s 时自动校准航向」，但"零滥用权限"要求默认不申请位置权限。**裁定**：默认用太阳方位角 + 本地时间 + 用户手动方位/最后已知位置（用户主动输入）推演；GPS 校准作为降级页内的**可选开关**，开启时才申请位置权限，并在文案上明确标注"需额外授权"。此项须在验收记录中登记为设计稿的合理偏离。
- `sound_meter` 的 WHO 对照表属于静态知识数据，打包为本地资源，不联网获取。
- `unit_converter` 的"离线汇率"必须显示快照日期，避免用户误以为实时汇率。

### 8.4 隐私与商店合规

- Manifest 权限最小化；无 `INTERNET`、无 `QUERY_ALL_PACKAGES`、无后台定位。
- 无第三方统计/崩溃/广告 SDK；无动态代码下发；无 WebView。
- 相机帧仅在内存分析、音频字节算完即弃、密码不落盘。
- 隐私政策说明"所有数据仅存于本设备"，与设置页「离线纯粹性承诺」文案一致。
- 目标渠道：Google Play 与国内主流商店（各自权限声明与隐私清单）。

---

## 9. 性能与健壮性指标

| 项 | 目标 | 手段 | 验证 |
|---|---|---|---|
| 冷启动 | ≤ 800ms（中端机） | 无启动页、无网络初始化、Hilt 惰性注入、基线 Profile | Macrobenchmark |
| 大图 | 48MP 不 OOM，堆峰值 < 16MB（压缩）/ <24MB（转码） | inSampleSize / ImageDecoder / 分块 / 熔断 | 大图压测 + LeakCanary |
| 前台转码 | 杀界面仍继续，WakeLock 及时释放 | Foreground Service + WorkManager | 场景测试 |
| 传感器 | 采样 ≤ 60fps、页面不可见即注销 | 生命周期感知注册 | 电量/CPU Profiling |
| 分贝计 | 波形 60FPS 不卡 UI | 环形缓冲 + Canvas draw 节流 | GPU 渲染分析 |
| 扫码 | 目标 60fps（按设备降级） | ImageAnalysis STRATEGY_KEEP_ONLY_LATEST | 帧率统计 |
| 数据库 | 主线程零 IO | 全量 `Dispatchers.IO` + Flow | StrictMode |
| 崩溃 | 权限拒绝/无传感器/超大输入/非法 JSON 均不崩 | 统一 Result + 状态机 | 边界用例集 |

---

## 10. 现有源码复用计划

### 10.1 25 个代码块 → 目标文件映射

| 来源 | 代码块 | 目标模块/文件 | 状态 |
|---|---|---|---|
| `jetpack_compose.txt` §1.1 | `ToolRegistry.kt` | `:core:model` / `:core:data` | 骨架，需接 Hilt Multibindings |
| §1.2 | `AppNavHost.kt` | `:app:navigation` | 骨架，需补齐 13 路由 |
| §2 | `HomeDashboardScreen.kt`（§1.2 第二块，包名 `feature.home`） | `:feature:home` | 需按 `home_dashboard/code.html` 逐控件校对 |
| §3.1–3.4 | `FormatConverterContract/Screen/TranscodingProgressScreen/ConversionReportScreen.kt` | `:feature:formatconverter` | 4 文件，覆盖 T7 三屏 |
| §4.1–4.2 | `AudioDecibelEngine.kt` / `SoundMeterScreen.kt` | `:feature:decibel` | 覆盖 T11 |
| §5 | Compass 代码块（包名 `feature.compass`） | `:feature:compass` | 覆盖 T10 |
| §6.1 | `CountdownEntity.kt` | `:feature:countdown:data` | 实体，需补 DAO/Repository/农历 |
| §7.1 | `RandomWheelScreen.kt` | `:feature:randomwheel` | 覆盖 T13 主屏 |
| §8 | Exif 代码块（包名 `feature.exif`） | `:feature:exif` | 覆盖 T6 |
| §9 | Password 代码块（包名 `feature.password`） | `:feature:password` | 覆盖 T3 |
| §10 | `TimestampCalculator` 等（包名 `feature.timestamp`） | `:feature:timestamp` | 覆盖 T12 |
| `compose.md` 1–5 | `QrScanContract/ViewModel/Screen/PermissionRationaleView/ScanningActiveView.kt` | `:feature:qrcode` | **最完整，可直接落地**，覆盖 `_4`/`_5` |
| `oom_2.md` 1–5 | `ImageCompressContract/Engine/ViewModel/Screen/CompareSplitView.kt` | `:feature:imagecompress` | **最完整，可直接落地**，覆盖 `oom_1` |

> 上述 Kotlin 均为文档内的代码文本，需按 §10.3 抽取为 `.kt` 文件后编译验证；包名统一为 `com.omnibox.*`，与 `_16.md` 模块拓扑一致。

### 10.2 缺口清单（需新写）

- `:core:designsystem` 全部 token 与 7 个基础组件（设计稿还原的基础，优先级最高）；
- `:core:common` / `:core:data`（Room Database、DAO、DataStore、Repository）；
- T4 单位换算、T8 文本散列、T9 开发者箱、T2 生码配置细化、T14 农历与 Glance 小组件；
- 全局能力 G1–G10（搜索、收藏、历史、导出、前台服务、权限审计）；
- 33 屏的**截图比对测试**与功能对照验收用例；
- `qr_history`、11 个空/权限/降级/进度/报告态的 Compose 页面（源码文档只覆盖了主屏）。

### 10.3 接入顺序

1. 抽取 25 个代码块为 `.kt`，建立模块骨架并编译通过（不接 UI）；
2. 先落 `:core:designsystem` 并做 1 屏（首页）像素级比对，校准 token；
3. 再逐屏接入，每接入一屏即跑一次截图比对 + 对应 UseCase 单元测试；
4. 补齐缺口模块，最后统一做权限/降级/性能专项。

---

## 11. 里程碑与排期

### 11.1 阶段表（1 名 Android 工程师，8 周）

| 阶段 | 周 | 目标 | 交付物 | 阶段验收 |
|---|---|---|---|---|
| P0 基线固化 | W1 上 | 资产规范化、页面 ID 映射、色值裁定、抽取 25 个 `.kt` | 附录 A 对照表、Design Token 模块、可编译空壳工程 | `./gradlew assembleDebug` 通过；Manifest 无 INTERNET |
| P1 设计系统与主框架 | W1 下–W2 | 7 个基础组件 + 底部 3 Tab + 首页/收藏/设置/历史/搜索 7 屏 | `:core:designsystem`、`:feature:shell`、ToolRegistry | 首页截图与 `home_dashboard/screen.png` 比对通过；13 款工具由注册表驱动 |
| P2 技术验证 | W2 下 | FFmpeg-Kit 裁剪 / ML Kit bundled / 农历库 / 大图 OOM 四项 Spike | 技术决策记录（含 §5.4 文案保留或修订结论） | 4 项 Spike 有可运行 Demo 与内存数据 |
| P3 无权限工具 | W3–W4 | T2/T3/T4/T8/T9/T12/T13/T14 共 8 款 + 其空/编辑态 | 8 个 feature 模块 | §5.2 对应行逐条功能验收；飞行模式可用 |
| P4 权限与硬件 | W5 | T1 扫码（活跃/权限/历史）、T10 指南针（活跃/降级）、T11 分贝（活跃/权限） | 3 个 feature 模块 + 6 个状态屏 | 权限拒绝/无传感器降级路径全通 |
| P5 图像与转码 | W6–W7 | T5 压缩、T6 EXIF、T7 转换（配置/进度/报告） | 3 个 feature 模块 + 前台服务 + Room 任务表 | 48MP 不 OOM；批量转码与报告数值真实 |
| P6 全局能力与打磨 | W7 下 | G1–G10 补齐、小组件、深链、深色模式、导出备份 | 全量能力 | 深浅色 33 屏比对；小组件免启动直达 |
| P7 测试与发布 | W8 | 33 屏比对、功能矩阵验收、性能/OOM/权限专项、release 包 | 比对报告、验收表、`.aab` | §12 发布门禁全绿 |

### 11.2 与 `_16.md`「3 周计划」的关系

原计划按 W1 基座+基础工具 / W2 多媒体与硬件 / W3 小组件与验收排布，**只覆盖 13 款工具的主路径**，未计入 33 个界面状态、深色模式、降级态、截图比对与前台服务。本计划保留其模块划分与 ToolRegistry 设计，但把周期扩展为 8 周；若必须 3 周交付，建议收敛为 **MVP**：只做首页 + 5 款无权限工具（T3/T4/T8/T12/T13），其余屏以"未启用"状态在设置页隐藏，并在发布说明中明确，避免违反 C2/C3。

### 11.3 并行建议

- P1 的 Design System 与 P2 的技术 Spike 可并行；
- 截图比对脚本（取 `screen.png` 与真机截图做结构比对）应在 P1 就写好，后续每屏自动跑；
- 每完成一个工具，同步补 §5 对应行的验收用例，避免最后集中补测试。

---

## 12. 测试与验收

### 12.1 UI 逐屏比对（C2 门禁）

对 32 个需实现的界面状态逐一执行：

1. 真机/模拟器按设计稿分辨率截图；
2. 与对应 `screen.png` 比对：控件种类与数量、相对位置、文案逐字、图标 ligature、主色/容器色、圆角、字号层级、状态栏与底部导航；
3. 差异分三级：**阻断**（缺控件/改布局/改文案）、**需登记**（§2.3 的合理偏离）、**可接受**（系统状态栏、字体渲染微调）；
4. 阻断项为 0 才算该屏通过；32 屏全通过方可进入发布。

### 12.2 功能对照验收（C3 门禁）

以 §5.2 / §5.3 的每一行作为一条用例：**每个可见字段注入输入后必须变化，每个可点击控件必须产生可观测结果**。特别针对 §2.5 的 18 类写死值逐一验证其为动态值。

### 12.3 专项测试

| 专项 | 用例 |
|---|---|
| 离线 | 飞行模式下 32 屏全可用；静态扫描无网络调用；`aapt dump permissions` 无 INTERNET |
| 权限 | 首次拒绝、永久拒绝、授权后返回、使用中撤销，4 条路径均进对应状态屏 |
| 降级 | 无地磁传感器、无相机、无麦克风、低内存、无通知权限 |
| 性能 | 48MP 大图、3 文件批量转码、10 秒音频采样、1 万字符文本、超长正则 |
| 隐私 | 无音频文件、无相机帧落盘、密码不入库、无第三方 SDK 流量 |
| 主题 | 浅/深切换、动态色彩开/关、字体放大 200%、折叠屏与平板断点 |
| 数据 | 历史批量删除、一键粉碎、导出导入、Room 迁移 |

### 12.4 发布门禁

33 屏比对全通过 + §5 矩阵 100% 覆盖 + 专项全绿 + release 包 R8 混淆后功能回归 + 权限与隐私清单签署。

---

## 13. 风险登记册

| # | 风险 | 概率 | 影响 | 对策 | 触发点 |
|---|---|---|---|---|---|
| R1 | FFmpeg-Kit 体积/ABI/许可证导致无法离线裁剪 | 高 | 高 | P2 Spike；不达标则收敛为图片转换 + 改文案（§5.4） | W2 |
| R2 | ML Kit 按需下载模型违反 C4 | 中 | 高 | 用 bundled 依赖或纯 ZXing 静态解析 | W5 |
| R3 | 33 屏逐屏还原工作量被低估 | 高 | 高 | P1 先做首页校准 token 与比对脚本；按屏验收不集中返工 | W1 |
| R4 | 编号目录洗牌导致实现串页 | 中 | 中 | 一律用附录 A 页面 ID，禁用 `_N` 作为需求编号 | 全程 |
| R5 | 设计稿文案与零权限冲突（GPS/汇率/60fps/NDK） | 高 | 中 | §8.3 / §5.4 裁定表，偏离逐条登记 | P2 |
| R6 | 大图 OOM / 前台服务被系统杀 | 中 | 高 | 采样 + 分块 + 前台服务 + WakeLock + 断点续转 | P5 |
| R7 | 农历与动态色彩在低版本设备表现不一致 | 中 | 中 | 离线农历表 + `minSdk 26` 版本分支 | P3 |
| R8 | 深色模式对比度失效 | 中 | 中 | 按 `DESIGN.md` 深色 token 配对 + 自动对比度检查 | P6 |

---

## 14. 交付物清单

1. Android 工程源码（18 个 Gradle 模块，Kotlin/Compose）；
2. `design-tokens` 模块与设计稿 token 对照表；
3. 32 屏截图比对报告（含偏离登记）；
4. §5 功能对照验收表（逐行签字）；
5. 权限矩阵与隐私合规说明；
6. 性能报告（冷启动、内存峰值、帧率、吞吐）；
7. debug / release `.apk` 与上架 `.aab`；
8. 用户可见的发布说明（说明 MVP 范围，若采用 3 周收敛方案）。

---

## 附录 A：33 个屏目录 → 页面 ID 对照（以新版为准）

| 目录 | 页面 ID | 目录 | 页面 ID |
|---|---|---|---|
| `home_dashboard` | `home` | `omnibox_offline_utility_suite` | 旧副本，不实现 |
| `favorites` | `favorites` | `settings` | `settings` |
| `tool_settings` | `tool_settings` | `tool_history` | `tool_history` |
| `_9` | `search_result` | `_4` | `qr_scan_active` |
| `_5` | `qr_permission` | `zxing` | `qr_generate` |
| `_11` | `qr_export_config` | `_2` | `qr_history` |
| `oom_1` | `image_compress` | `exif_2` | `exif_risk_report` |
| `exif_1` | `exif_clean_config` | `format_converter` | `format_converter` |
| `_12` | `transcoding_progress` | `_7` | `conversion_report` |
| `text_hash` | `text_tools` | `_13` | `text_tools_empty` |
| `dev_tools` | `dev_tools` | `_8` | `compass_active` |
| `_15` | `compass_degraded` | `sound_meter` | `sound_meter` |
| `_6` | `sound_permission` | `password_generator` | `password` |
| `unit_converter` | `unit_converter` | `_3` | `timestamp` |
| `_10` | `random_wheel` | `_18` | `wheel_editor` |
| `_1` | `countdown_empty` | `_17` | `countdown_list` |
| `_14` | `countdown_editor` | — | — |

> 另有 `tonal_utility/`（仅 `DESIGN.md`）与根目录 4 份文档，非界面目录。

## 附录 B：工具 ID / 路由 / 分类 / 权限

| 工具 | ID | 路由 | 分类 | 权限 |
|---|---|---|---|---|
| 二维码扫码与生成 | `qrcode` | `tool/qrcode` | 日常生活 | CAMERA（可降级） |
| 单位换算 | `unit_converter` | `tool/unit_converter` | 日常生活 | 无 |
| 随机决策转盘 | `random_wheel` | `tool/random_wheel` | 日常生活 | 无 |
| 密码生成器 | `password` | `tool/password` | 日常生活 | 无 |
| 倒计时与纪念日 | `countdown` | `tool/countdown` | 日常生活 | 通知（13+） |
| 时间戳推算 | `timestamp` | `tool/timestamp` | 开发调试 | 无 |
| 文本与散列 | `text_tools` | `tool/text_tools` | 开发调试 | 无 |
| 开发者全能箱 | `dev_tools` | `tool/dev_tools` | 开发调试 | 无 |
| 图片无损压缩 | `image_compress` | `tool/image_compress` | 媒体图像 | 无（Photo Picker） |
| 全能格式转换 | `format_converter` | `tool/format_converter` | 媒体图像 | 无（前台服务通知） |
| EXIF 隐私擦除 | `exif` | `tool/exif` | 媒体图像 | 无（Photo Picker） |
| 指南针与水平仪 | `compass` | `tool/compass` | 传感器硬件 | 位置（可选） |
| 环境分贝计 | `decibel` | `tool/decibel` | 传感器硬件 | RECORD_AUDIO |

## 附录 C：资产文件索引

- `stitch_\stitch_\` — 33 × `code.html`、32 × `screen.png`
- `tonal_utility/DESIGN.md` — 274 行设计系统
- `_16.md` — 121 行 PRD/架构/3 周计划（=`旧版 _11.md`）
- `compose.md` — 879 行扫码模块 Kotlin
- `oom_2.md` — 1227 行图片压缩防 OOM Kotlin
- `jetpack_compose.txt` — 1826 行全模块 Kotlin（15 代码块 / 10 模块）

## 附录 D：术语

**C1–C5** 硬约束 · **T1–T14** 工具能力编号 · **G1–G10** 全局能力编号 · **P0–P7** 里程碑 · **DoD** 完成定义 · **Tonal Elevation** M3 以表面色阶替代阴影表达层级 · **ToolRegistry** 工具动态注册表 · **MVI** Model-View-Intent。

---

## 15. 构建、签名与发布（执行补充，2026-11-10）

> 细则已落到工程内的 `OmniBox/BUILD.md`、`OmniBox/app/build.gradle.kts`、
> `OmniBox/app/proguard-rules.pro`、`OmniBox/.github/workflows/android-build.yml`
> 与 `OmniBox/scripts/verify-release.sh`。本章为计划层的决策记录。

### 15.1 产物与命令

| 任务 | 产物 | 用途 |
|---|---|---|
| `./gradlew :app:assembleDebug` | `app-debug.apk` | 自动 debug 签名，`adb` 自测 |
| `./gradlew :app:assembleRelease` | `app-release.apk` | 国内商店 / 侧载分发 |
| `./gradlew :app:bundleRelease` | `app-release.aab` | Google Play（Play App Signing 再拆分签名） |

图形化 **Build → Generate Signed App Bundle / APK** 只用于首次生成密钥；长期固定 Gradle + CI 保证可复现。

### 15.2 签名策略

- `keytool` 生成一次 keystore，`keystore.properties`（已 gitignore）或 CI 四个环境变量注入，**密钥与口令绝不入库**。
- v1/v2/v3 全开；v4（`.idsig`）不需要。上架 Play 用 upload key 签 AAB，侧载 APK 与 Play 版签名不同，禁止混装覆盖。
- `BuildConfig.VERSION_DISPLAY` 驱动设置页版本号，界面不写死。

### 15.3 发布门禁（缺一不可）

1. `apksigner verify --verbose --print-certs` 通过；
2. `aapt dump permissions` 与 `merged_manifests/release/AndroidManifest.xml` **均查不到 INTERNET**（防传递依赖注入）；
3. `aapt dump badging` 确认 versionCode / sdk / 非 debuggable；
4. `sha256sum` 留档；
5. 飞行模式下 `adb install -r` 冒烟 32 屏；
6. 32 屏截图比对报告 + 功能矩阵验收表齐备（§12 双门禁）。

### 15.4 五个打包风险

零网络权限被 ML Kit / Firebase 传递依赖加回 · 离线资源（Roboto Flex / Material Symbols / 农历表 / 汇率快照 / bundled 模型）必须随包且 `screen.png` 不进 APK · NDK `.so` 分 ABI · R8 keep（Hilt/Room/反射/JNI）· 多渠道用 `productFlavors` 而非签名后写渠道号。

---

## 16. 执行状态回执（P0 / P1）

按「按照计划开始执行」已完成代码落地，产物位于 `软件开发\OmniBox\`：

- **18 个 Gradle 模块**（1 app + 4 core + 13 feature），版本目录统一，Hilt / Room(KSP) / DataStore / Navigation 按 §6 接入。
- **设计系统**：`DESIGN.md` 的浅/深色 token、15 级字阶、8dp 栅格、28dp 圆角、7 个基础组件全部编码；主色按裁定取 `#4F378A`。
- **数据层**：10 张 Room 表、13 项 DataStore 偏好、`ToolCatalog`（Hilt `@IntoSet`），首页/收藏/搜索/设置计数同源。
- **12 个界面状态接真实逻辑**：主框架 6 屏 + 4 款工具页 + 开发者箱 + 文本空态；其余 20 屏按 P2–P7 排期，占位页明确标注状态、不做假交互。
- **4 款工具真实算法**：`SecureRandom` 密码生成器、`java.time` 时间戳、`MessageDigest`/Base64/URL/JSON/进制/正则文本与开发者箱、`BigDecimal` 七类单位换算。每个复制/开关/滑块都接真实行为。
- **参考源码**：设计包 26 个 Kotlin 代码块抽到 `reference/kt/`（不参与编译，理由见 `IMPLEMENTATION_STATUS.md`）。
- **静态自检**：`python scripts/check-project.py`，覆盖模块拓扑、alias、package、括号、零网络权限、注册表 13 条、设计基线 33 屏。
- **未完成**：本机无 JDK / Gradle / Android SDK，工程**未经真实编译**；`gradle-wrapper.jar` 二进制未随附（补齐方式见 `BUILD.md §2`）；首次 Sync 可能暴露依赖版本或 API 细节问题。

逐项进度、假数据→真实数据源对照、已知偏差见 `OmniBox/IMPLEMENTATION_STATUS.md`。
