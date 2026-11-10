# OmniBox / 万能工具箱 — Android 工程

按 `../OmniBox-Android开发计划.md` 执行落地的 **Android 原生工程**（Kotlin 2.0 + Jetpack Compose + Material 3），
不是网页、不是套壳。设计基线为 `../stitch_/stitch_/`（33 屏 `code.html` + 32 张 `screen.png` + `DESIGN.md`）。

## 两条不可协商约束

1. 界面按设计稿还原：布局、控件、文案、图标、配色、层级不重构；仅把设计稿里的演示数值换成真实数据。
2. 界面上每个数值、开关、按钮都有对应后端实现；排期中的模块显示明确状态提示，**不做假交互**。

## 目录

```
OmniBox/
├── app/                       Application、MainActivity、AppNavHost、签名、混淆、资源
├── core/
│   ├── designsystem/          Color/Type/Shape/Dimens/Theme + OmniIcon + 7 个基础组件
│   ├── common/                OmniResult / Dispatcher / Formatters
│   ├── model/                 ToolDefinition / ToolCategory / ScreenId / Routes
│   └── data/                  ToolCatalog(@IntoSet) / Room / DataStore 偏好
├── feature/
│   ├── home/                  首页 + 常用 + 设置 + 工具设置 + 历史 + 搜索（6 屏）
│   ├── password/              SecureRandom 密码生成器
│   ├── timestamp/             java.time 双向换算
│   ├── texttools/             文本全能箱 + 开发者箱（含空态）
│   ├── unitconverter/         BigDecimal 七类单位换算
│   ├── qrcode/                CameraX + ZXing 实时扫码 / 生成 / 历史
│   ├── imagecompress/         防 OOM 三段式采样压缩 + 分割对比
│   ├── exif/                  EXIF 风险审计 + 重编码擦除 + 回读验证
│   ├── formatconverter/       PNG/JPEG/WEBP 批量真实转码 + 报告
│   ├── compass/               磁力计/重力融合罗盘 + 水平仪 + 真北校正
│   ├── decibel/               AudioRecord 声压级 / Leq / WHO 对照
│   ├── randomwheel/           SecureRandom 加权抽样 + Canvas 转盘
│   └── countdown/             Room 事件 + 年度重复 + AlarmManager 提醒
├── reference/kt/              从设计包抽取的 26 个 Kotlin 代码块（参考，不参与编译）
├── scripts/                   环境自检 / 静态自检 / 发布前校验
├── .github/workflows/         GitHub Actions 云构建（零本机 SDK 也能出 APK）
├── BUILD.md                   构建、签名、发布、CI 与本机 SDK 安装记录
└── IMPLEMENTATION_STATUS.md   当前实现进度与已知偏差
```

## 快速开始

```bash
python scripts/check-env.py       # 环境自检：JDK 17 / Android SDK 35 / wrapper jar
python scripts/check-project.py   # 工程静态自检（不需要 JDK/SDK）
./gradlew :app:assembleDebug      # 产物 app/build/outputs/apk/debug/app-debug.apk
```

Windows 用 `gradlew.bat`。本机环境路径与 CI 用法见 `BUILD.md`。

## 架构要点

- **MVI**：`StateFlow<UiState>` + `sealed UiEvent`，ViewModel 不把异常抛到 UI。
- **ToolRegistry**：13 个 feature 模块用 Hilt `@IntoSet` 贡献 `ToolDefinition`，首页/收藏/搜索/设置的计数与列表全部由 `ToolCatalog` 驱动，杜绝「界面写 13 款、实际只有 12 款功能」。
- **零网络权限**：主 Manifest 用 `tools:node="remove"` 移除传递依赖注入的 `INTERNET`；发布前检查合并产物。
- **隐私**：`allowBackup=false` + 全量排除备份规则；密码本体、音频字节、相机帧不落盘；历史只记元信息。
- **深链**：`omnibox://tool/{tool_id}` 直达工具页。

## 构建状态（2026-11-10）

本机 JDK 17.0.21 + Android SDK（Platform 35 / Build-Tools 35.0.0）+ Gradle 8.9 Wrapper 齐备，
`:app:assembleDebug` **真实编译通过**（0 错误 0 警告），产出 `app-debug.apk`（约 21.2 MB，v2 debug 签名，可直接 `adb install`）。
`apksigner verify` 通过；`minSdk 26 / targetSdk 35 / compileSdk 35`；合并后的 Manifest 已核验**不含任何网络权限**。
两个源码级门禁 `:verifyNoInternetPermission`、`:app:assertOfflineManifest` 均通过。

首次真实编译暴露并修掉了 5 处静态自检查不出的问题（中文路径开关、`ndk{}` 层级、模块依赖、Compose 测试依赖、缺失路由常量），
逐项记录在 `IMPLEMENTATION_STATUS.md`。

## 当前进度

P0–P5 已完成：**13 款工具全部接入真实实现**，33 屏中 32 屏有对应界面（唯一例外是计划裁定不实现的旧版首页副本），
权限全部按需申请、拒绝即走设计稿降级页，工程内已无占位页。P6（前台服务 / Glance 小组件）与
P7（截图比对、release 签名包）尚未开始，逐项记录在 `IMPLEMENTATION_STATUS.md`。
