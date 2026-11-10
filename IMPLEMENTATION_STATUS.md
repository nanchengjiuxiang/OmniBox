# 实现进度与已知偏差（2026-11-10，P2 批次完成）

**结论：13 款工具全部接入真实实现，工程内已无 `PendingToolScreen` 占位页（仅保留未知 toolId 的兜底分支）。**
本机 JDK 17 + Android SDK 35 + Gradle 8.9 环境下 `:app:assembleDebug` 编译通过，产出可安装 debug APK。

## 里程碑

| 阶段 | 计划内容 | 状态 |
|---|---|---|
| P0 | 固化设计基线、抽取 Kotlin 代码块、建立工程根目录 | ✅ 完成 |
| P1 | 设计系统、模块拓扑、主框架 6 屏、4 款无权限工具 | ✅ 完成 |
| **P2** | **扫码 / 图片压缩 / EXIF / 格式转换 / 转盘 / 倒计时 / 指南针 / 分贝计 + 权限降级** | **✅ 本次完成（8 款工具，约 9,500 行新代码）** |
| P3 | 工具页与设计稿逐屏校对 | ✅ 随 P2 一并还原（33 屏中 32 屏有实现） |
| P4 | 传感器类工具与降级页 | ✅ 指南针 / 分贝计完成 |
| P5 | 转盘 / 倒计时与 Room 持久化 | ✅ 完成 |
| P6 | 前台服务、Glance 小组件 | ⏳ 未做（见偏差 12） |
| P7 | 32 屏截图比对报告、release 签名包 | ⏳ 未做（见偏差 13、14） |

## 13 款工具

| 工具 | 页面 ID | 状态 | 真实实现要点 | 权限 |
|---|---|---|---|---|
| 二维码 | `qr_scan_active` 等 5 屏 | ✅ | CameraX 实时预览 + ZXing 多格式解码；本地生成（L/M/Q/H 纠错、尺寸、配色、留白）→ PNG 导出；相册图片解析；历史落 Room | CAMERA（可降级） |
| 单位换算 | `unit_converter` | ✅ | `BigDecimal` 七类物理量、温度 offset、精度档位、基准速查 | 无 |
| 随机转盘抽签 | `random_wheel` / `wheel_editor` | ✅ | `SecureRandom` 按权重抽样、Canvas 扇区绘制与旋转动画、Room 预设与选项增删改 | 无 |
| 倒计时/纪念日 | `countdown_*` 3 屏 | ✅ | Room 事件 CRUD、每秒实时刷新、年度重复取下一周年、`AlarmManager` + 广播接收器真提醒 | 通知（13+） |
| 图片无损压缩 | `image_compress` | ✅ | 三段式防 OOM 采样、质量/最大边长/格式三轴、前后分割对比、SAF 导出 | 无（Picker） |
| EXIF 隐私擦除 | `exif_*` 2 屏 | ✅ | `ExifInterface` 真实读 tag → 风险分级 → 重编码擦除 → 回读验证前后差异 | 无（Picker） |
| 指南针/水平仪 | `compass_*` 2 屏 | ✅ | 加速度+磁场旋转矩阵、低通滤波、Canvas 罗盘、pitch/roll 水平仪；真北可选校正 | 位置（可选） |
| 环境分贝计 | `sound_meter` / `sound_permission` | ✅ | `AudioRecord` PCM→RMS→dBFS→校准 SPL；瞬时/滑动平均/Leq/峰值;WHO 对照 | RECORD_AUDIO |
| 格式转换 | `format_converter` 等 3 屏 | ✅ | PNG/JPEG/WEBP 真实转码、多选批量、逐张真实进度、Room 任务与产物记录、报告聚合 | 无（Picker） |
| 安全密码生成 | `password` | ✅ | `SecureRandom`、熵值、破解耗时、字典校验、Diceware、批量生成 | 无 |
| 时间戳转换 | `timestamp` | ✅ | `java.time` 秒/毫秒双向、5 时区、ISO/周序数、时间差 | 无 |
| 文本全能箱 | `text_tools` / `text_tools_empty` | ✅ | MD5/SHA、Base64、URL、7 种清洗、字符统计 | 无 |
| 代码与格式化 | `dev_tools` | ✅ | JSON 美化校验、进制联动、正则匹配/捕获组、URL 参数拆解 | 无 |

## 本次新增的工程文件

| 模块 | 文件 | 职责 |
|---|---|---|
| `feature:qrcode` | `QrcodeEngine.kt` / `QrcodeViewModel.kt` / `QrcodeScreen.kt` / `QrcodePages.kt` | 编解码、相机管线、5 个页面状态 |
| `feature:decibel` | `DecibelEngine.kt` / `DecibelViewModel.kt` / `DecibelScreen.kt` | 音频采集与声压统计 |
| `feature:compass` | `CompassEngine.kt` / `CompassViewModel.kt` / `CompassScreen.kt` | 传感器融合、罗盘与水平仪绘制 |
| `feature:imagecompress` | `ImageCompressEngine.kt` / `ImageCompressViewModel.kt` / `ImageCompressScreen.kt` | 防 OOM 压缩与对比视图 |
| `feature:exif` | `ExifEngine.kt` / `ExifViewModel.kt` / `ExifScreen.kt` | 元数据审计与擦除验证 |
| `feature:formatconverter` | `FormatConverterEngine.kt` / `FormatConverterViewModel.kt` / `FormatConverterScreen.kt` | 批量转码与报告 |
| `feature:randomwheel` | `RandomWheelEngine.kt` / `RandomWheelViewModel.kt` / `RandomWheelScreen.kt` | 加权抽样与转盘 |
| `feature:countdown` | `CountdownEngine.kt` / `CountdownViewModel.kt` / `CountdownScreen.kt` / `CountdownReminderReceiver.kt` | 事件、周年、提醒 |

## 33 屏覆盖

| 类别 | 数量 | 状态 |
|---|---|---|
| 主框架 6 屏（home / favorites / settings / tool_settings / tool_history / search_result） | 6 | ✅ 文案与控件按 `code.html` 还原，计数与权限状态接真实数据 |
| 二维码 5 屏 | 5 | ✅ 扫描 / 权限 / 生成 / 导出配置 / 历史（页内状态切换） |
| 图片压缩 1 屏 | 1 | ✅ |
| EXIF 2 屏 | 2 | ✅ 风险报告 / 擦除配置 |
| 格式转换 3 屏 | 3 | ✅ 选择 / 进度 / 报告 |
| 指南针 2 屏 | 2 | ✅ 正常 / 降级 |
| 分贝计 2 屏 | 2 | ✅ 测量 / 权限 |
| 转盘 2 屏 | 2 | ✅ 主盘 / 编辑器 |
| 倒计时 3 屏 | 3 | ✅ 空态 / 列表 / 编辑器 |
| 密码、单位、时间戳、文本箱 2 屏、开发者箱 | 6 | ✅ |
| `omnibox_offline_utility_suite` | 1 | 计划裁定为旧版首页副本，不实现 |

## 权限与降级矩阵（零 INTERNET 不变）

| 权限 | 申请时机 | 拒绝后的真实行为 | 对应设计稿降级页 |
|---|---|---|---|
| `CAMERA` | 进入扫码页主动申请 | 关闭实时预览，保留「相册解析」「生成二维码」两条可用路径，并提示如何重开 | `qr_permission` |
| `RECORD_AUDIO` | 点开始测量时申请 | 不创建 `AudioRecord`，显示权限说明与重试入口，不显示假数值 | `sound_permission` |
| `ACCESS_FINE_LOCATION`（可选） | 用户点「校准真北」才申请 | 保持磁北，明示「真北需定位授权」，不伪造偏角 | `compass_degraded` |
| `POST_NOTIFICATIONS`（13+） | 打开「提醒」开关时申请 | 事件仍可保存与页内倒计时，仅提醒不可用且界面明示 | 倒计时编辑器内提示 |
| `SCHEDULE_EXACT_ALARM`（12+） | 设置提醒时 | 捕获 `SecurityException`，退回非精确闹钟并提示可能延迟 | 同上 |

硬件特性全部 `required="false"`：无磁力计 / 无麦克风 / 无相机的设备走到对应降级分支，不崩溃。

## 新增依赖与体积

| 依赖 | 版本 | 用途 | 是否引入网络权限 |
|---|---|---|---|
| `com.google.zxing:core` | 3.5.3 | 二维码编解码（纯算法） | 否 |
| `androidx.camera:camera-core/camera2/lifecycle/view` | 1.3.4 | 相机预览与帧分析 | 否 |
| `androidx.exifinterface:exifinterface` | 1.3.7 | 读取/校验 EXIF | 否 |

APK 由 18.2 MB 增至约 **21.5 MB**（新增 ZXing + CameraX + ExifInterface 与 8 个模块的代码资源）。
**未引入** FFmpeg-Kit、ML Kit、Coil/Glide、任何网络库：前三者会显著增大包体或注入 `INTERNET`，后者违反零权限承诺。

## 已验证项

| 项 | 结果 |
|---|---|
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL，0 编译错误 0 警告 |
| `python scripts/check-project.py` | 0 错误 0 警告（18 模块 / 13 条注册 / 77 个 Kotlin 文件） |
| `aapt dump permissions` | 仅 CAMERA、RECORD_AUDIO、ACCESS_FINE/COARSE_LOCATION、POST_NOTIFICATIONS、VIBRATE、SCHEDULE_EXACT_ALARM、USE_EXACT_ALARM；**无 INTERNET / ACCESS_NETWORK_STATE** |
| merged manifest 扫描 | `merged_manifest/debug/.../AndroidManifest.xml` 与 `merged_manifests/debug/...` 均为 0 处 `INTERNET` |
| `apksigner verify` | v2 = true，`CN=Android Debug` |
| 产物 | `app/build/outputs/apk/debug/app-debug.apk`，21,453,978 字节（约 20.5 MiB） |
| SHA-256 | `ab749c0c370594ff3085461c9aaa51ac9db7b4a9eae55526e2c35d7dec09b40a` |

## 已知偏差与取舍（均可复现、均有代码注释）

1. **分类计数按真实归类**：设计稿 Chip 写「媒体图像 (2)」，实际 13 款工具的真实归类为媒体图像 3、传感器硬件 2，界面显示真实值。
2. **`<h1>` 模板残留**：`unit_converter`、`_3`、`dev_tools` 的标题与内容不符，按实际内容命名。
3. **Roboto Flex 字体缺失**：设计包不含字体二进制，暂用 `FontFamily.Default`，字阶已按 `DESIGN.md` 落地。
4. **语音输入未实现**：首页/搜索的 `mic` 按钮存在但不弹假结果；本地语音识别需额外模型与依赖。
5. **离线汇率是内置快照**：界面已标注「不联网更新，仅作估算参考」。
6. **Room `exportSchema = false`**：schema 归档尚未开启，后续版本迁移前需改为 true 并配置 `room.schemaLocation`。
7. **工程位于中文路径**：`android.overridePathCheck=true` 放行；aapt2 / D8 / apksigner 实测均正常。
8. **Material Symbols 为语义近似映射**：`OmniIcon.kt` 用已验证的 Compose 矢量图标兜底，换完整图标资源只需改这一个文件。
9. **EXIF 擦除采用「重编码」而非原地删 tag**：直接 `saveAttributes()` 会破坏用户原图且无法彻底清除，改为解码→重编码输出新文件→回读新文件确认风险项已消失。代价是二次压缩（JPEG 有轻微画质损失，PNG/WEBP 无损）。
10. **格式转换只覆盖位图格式**：PNG/JPEG/WEBP 互转是真实转码；音频/视频转码需要 FFmpeg-Kit（数十 MB、含 native 库），本批次未引入，界面不提供假按钮。
11. **真北校正依赖 `getLastKnownLocation`**：无缓存定位时返回 null，界面显示「仅磁北可用」；不伪造磁偏角。
12. **未做前台服务与 Glance 小组件**：`supportsWidget` 元数据已就绪，Widget 与前台服务属 P6。
13. **未做 32 屏截图比对报告**：需要模拟器/真机逐屏截图，本轮以 `code.html` 结构还原 + 编译验证为准。
14. **未产出 release 签名包**：缺 keystore，`keystore.properties` 未创建；`assembleRelease` 目前只会产出 unsigned 包。
15. **`exif` 工具 id 由 `exif_clean` 改为 `exif`**：与计划附录 B 对齐，路由同步改为 `tool/exif`。

## 工具链修复记录（本轮）

| 问题 | 原因 | 修法 |
|---|---|---|
| 10 个 `.kt` 被自检误报「括号不配对」 | `check-project.py` 用逐条正则剥离注释/字符串，先删 `//` 会破坏字符串里的 URL，导致引号错配 | 改为状态机 `strip_kotlin_literals()`，正确跳过行注释、块注释、三引号原始字符串、`\` 转义与字符字面量 |
| `Preview.setSurfaceProvider` 编译失败 | Kotlin 不能对 Java setter 方法做属性赋值 | 改为显式 `setSurfaceProvider(...)` |
| `Location?` 空安全报错 | 显式声明为可空后未判空 | 类型收紧为 `Location`，靠 `?: return null` 提前返回 |
| `nativeCanvas` / `drawIntoCanvas` 未解析 | 缺少 Compose 扩展导入 | 补 `androidx.compose.ui.graphics.nativeCanvas` 与 `...drawscope.drawIntoCanvas` |
| `@OptIn(ExperimentalGetImage::class)` 无效果告警 | 该注解不是 `@RequiresOptIn` | 改为直接标注 `@ExperimentalGetImage` |

## 下一步

1. P6：前台服务（转码/压缩长任务）+ Glance 小组件（二维码、倒计时）。
2. P7：模拟器逐屏截图与 `screen.png` 比对，产出 32 屏比对报告。
3. 生成 release keystore → `assembleRelease` → `verify-release.sh` 全量门禁。
4. 为 8 个 Engine 补 JVM 单测（加权分布、dB 换算、周年边界、采样率计算），把算法正确性纳入 CI。
