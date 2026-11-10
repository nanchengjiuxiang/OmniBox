# OmniBox — Android 工具箱工程

按 `../OmniBox-Android开发计划.md` 执行落地的 **Android 原生工程**（Kotlin 2.0 + Jetpack Compose + Material 3），
不是网页、不是套壳。设计基线为 `../stitch_/stitch_/`（33 屏 `code.html` + 32 张 `screen.png` + `DESIGN.md`）。

## 两条不可协商约束

1. 界面按设计稿还原：布局、控件、文案、图标、配色、层级不重构；仅把设计稿里的演示数值换成真实数据。
2. 界面上每个数值、开关、按钮都有对应后端实现；排期中的模块显示明确状态提示，**不做假交互**。

## 目录

```
OmniBox/
├── app/                       Application、MainActivity、AppNavHost、Glance 小组件、签名、混淆、资源
├── core/
│   ├── designsystem/          Color/Type/Shape/Dimens/Theme + OmniIcon + 7 个基础组件
│   ├── common/                OmniResult / Dispatcher / Formatters
│   ├── model/                 ToolDefinition / ToolCategory / ScreenId / Routes
│   └── data/                  ToolCatalog(@IntoSet) / Room / DataStore 偏好
├── feature/
│   ├── home/                  首页 + 常用 + 设置 + 工具设置 + 历史 + 搜索（6 屏）
│   ├── explore/               探索 · 精选导航（30 站点外链目录 / 我的收藏 / 自定义站点 / Chrome Custom Tabs）
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
│   └── countdown/             Room 事件 + 年度重复 + AlarmManager 提醒 + 开机重建
├── reference/kt/              从设计包抽取的 26 个 Kotlin 代码块（参考，不参与编译）
├── scripts/                   环境自检 / 静态自检 / 发布前校验 / 设计稿文案覆盖比对
├── .github/workflows/         GitHub Actions 云构建（零本机 SDK 也能出 APK）
├── BUILD.md                   构建、签名、发布、CI 与本机 SDK 安装记录
├── SCREEN_COVERAGE.md         32 屏设计文案覆盖报告（脚本生成）
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
- **网络边界**：只有探索页用 `INTERNET`（打开外链、拉站点图标）与 `ACCESS_NETWORK_STATE`（离线提示）；页面由系统浏览器 / Chrome Custom Tabs 渲染，App 不内置 WebView、不代理内容。13 款工具本体仍是纯本地运算。
- **隐私**：`allowBackup=false` + 全量排除备份规则；密码本体、音频字节、相机帧不落盘；历史只记元信息。
- **深链**：`omnibox://tool/{tool_id}` 直达工具页。

## 构建状态（2026-11-10）

本机 JDK 17.0.21 + Android SDK（Platform 35 / Build-Tools 35.0.0）+ Gradle 8.9 Wrapper 齐备：

| 任务 | 结果 |
|---|---|
| `./gradlew :app:assembleDebug` | ✅ 0 错误 0 警告，`app-debug.apk` 23.4 MB，`CN=Android Debug`，可直接 `adb install` |
| `./gradlew test` | ✅ 12 个测试类 / 144 条用例 × 2 变体 = 288 次执行，0 失败（探索模块新增 31 条） |
| `./gradlew :app:assembleRelease :app:bundleRelease` | ✅ `app-release.apk` 5.0 MB（R8 + 资源压缩）、`app-release.aab` 8.8 MB，RSA 4096 签名 |
| `bash scripts/verify-release.sh` | ✅ 签名 v1/v2/v3、网络权限策略（INTERNET + ACCESS_NETWORK_STATE）、合并 Manifest 复核、SHA-256 留档 |
| `python scripts/check-project.py` | ✅ 0 错误 0 警告 |
| `python scripts/check-screens.py` | 32 屏 / 1354 条设计文案 / 命中 835，覆盖率 61.7%（口径见 `SCREEN_COVERAGE.md`） |

`minSdk 26 / targetSdk 35 / compileSdk 35`；合并后的 Manifest 只含 `INTERNET` 与 `ACCESS_NETWORK_STATE` 两项网络权限，
源码级门禁 `:verifyNetworkPolicy`、`:app:assertNetworkPolicy` 与 `scripts/verify-release.sh` 均通过。

## 当前进度

P0–P7 全部完成：**13 款工具真实可用**，33 屏中 32 屏有对应界面（唯一例外是计划裁定不实现的旧版首页副本），
权限全部按需申请、拒绝即走设计稿降级页，工程内已无占位页。

- P6：转码 `dataSync` 前台服务（真实进度通知 + 取消）、倒计时与二维码两个 Glance 小组件、开机重建提醒。
- P7：144 条引擎单测接入 CI、release 签名 APK/AAB、32 屏文案覆盖报告。
- P8：**探索 · 精选导航**——30 个真实站点（10 个分类）+ 收藏 + 自建站点 + 无痕沙盒 + Chrome Custom Tabs，站点图标真实拉取、失败退化为字母头像。
- P9：**edge-to-edge 系统栏适配**——顶栏不再压状态栏、底栏不再被手势条遮挡，状态栏图标明暗跟随 App 主题。
- 已知偏差（如营销文案不搬进 App、无逐屏像素比对、release keystore 为本机自建）逐项记录在 `IMPLEMENTATION_STATUS.md`。
