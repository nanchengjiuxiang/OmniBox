# 实现进度与已知偏差（2026-11-10，P9 系统栏适配收口）

**结论：13 款工具全部真实可用，P6（前台服务、Glance 小组件）、P7（release 签名包、单测、覆盖比对）、P8（探索 · 精选导航）与 P9（edge-to-edge 系统栏适配）已收口。**
本机 JDK 17 + Android SDK 35 + Gradle 8.9 环境下 `assembleDebug` / `assembleRelease` / `bundleRelease` / `test` 全部通过。

## 里程碑

| 阶段 | 计划内容 | 状态 |
|---|---|---|
| P0 | 固化设计基线、抽取 Kotlin 代码块、建立工程根目录 | ✅ 完成 |
| P1 | 设计系统、模块拓扑、主框架 6 屏、4 款无权限工具 | ✅ 完成 |
| P2 | 扫码 / 图片压缩 / EXIF / 格式转换 / 转盘 / 倒计时 / 指南针 / 分贝计 + 权限降级 | ✅ 完成（8 款工具，约 9,500 行） |
| P3 | 工具页与设计稿逐屏校对 | ✅ 32 屏有实现，覆盖率报告见 `SCREEN_COVERAGE.md` |
| P4 | 传感器类工具与降级页 | ✅ 指南针 / 分贝计完成 |
| P5 | 转盘 / 倒计时与 Room 持久化 | ✅ 完成 |
| **P6** | **前台服务、Glance 小组件** | **✅ 本次完成（转码前台服务 + 倒计时/二维码两个小组件）** |
| P7 | 单测、release 签名包、覆盖比对报告 | ✅ 完成（144 条单测 / 签名 APK + AAB / 32 屏文案覆盖报告） |
| P8 | 探索 · 精选导航（新增需求） | ✅ 完成（30 个站点 / 10 个分类 / 收藏与自建站点 / Custom Tabs / 无痕模式） |
| **P9** | **系统栏适配（缺陷修复）** | **✅ 本次完成（21 处根容器 inset / 4 处 Scaffold / 底栏手势条 / 图标明暗跟随主题）** |

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

## 本次新增的工程文件（P6/P7）

| 模块 | 文件 | 职责 |
|---|---|---|
| `feature:formatconverter` | `TranscodeJobRunner.kt` | 应用级协程作用域的转码任务执行器，`StateFlow` 暴露真实进度，支持取消；ViewModel 销毁不影响任务 |
| `feature:formatconverter` | `TranscodeService.kt` | `dataSync` 前台服务：真实进度通知、通知栏取消、结束自动 `stopSelf` |
| `feature:countdown` | `CountdownAlarmScheduler.kt` | 提醒调度的唯一入口（ViewModel 与开机重建共用，避免 PendingIntent 形状不一致） |
| `feature:countdown` | `CountdownBootReceiver.kt` | 开机 / 应用更新后从 Room 读回事件并重建闹钟 |
| `app` | `widget/CountdownWidget.kt`、`widget/QrWidget.kt`、`widget/WidgetEntryPoint.kt`、`widget/CountdownWidgetRefreshReceiver.kt` | 两个 Glance 小组件 + Hilt EntryPoint + 数据变更刷新 |
| `app` | `res/xml/countdown_widget_info.xml`、`res/xml/qr_widget_info.xml` | 小组件尺寸、预览、更新周期 |
| 各模块 | `src/test/java/**/*Test.kt` | 144 条 JVM 单测（引擎层，无 Android 依赖；其中探索 31 条） |
| `scripts` | `check-screens.py` | 设计稿 → 实现的中文文案覆盖比对 |
| 根目录 | `SCREEN_COVERAGE.md` | 上者生成的 32 屏覆盖率报告 |

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

## 权限与降级矩阵（网络权限仅限探索页）

| 权限 | 申请时机 | 拒绝后的真实行为 | 对应设计稿降级页 |
|---|---|---|---|
| `CAMERA` | 进入扫码页主动申请 | 关闭实时预览，保留「相册解析」「生成二维码」两条可用路径，并提示如何重开 | `qr_permission` |
| `RECORD_AUDIO` | 点开始测量时申请 | 不创建 `AudioRecord`，显示权限说明与重试入口，不显示假数值 | `sound_permission` |
| `ACCESS_FINE_LOCATION`（可选） | 用户点「校准真北」才申请 | 保持磁北，明示「真北需定位授权」，不伪造偏角 | `compass_degraded` |
| `POST_NOTIFICATIONS`（13+） | 打开「提醒」开关时申请 | 事件仍可保存与页内倒计时，仅提醒不可用且界面明示 | 倒计时编辑器内提示 |
| `SCHEDULE_EXACT_ALARM`（12+） | 设置提醒时 | 捕获 `SecurityException`，退回非精确闹钟并提示可能延迟 | 同上 |
| `INTERNET` | 探索页加载站点图标、打开外链 | 断网时导航目录照常浏览，卡片顶部提示「当前无网络」；图标退化为首字母头像 | 探索页顶部提示条 |
| `ACCESS_NETWORK_STATE` | 进入探索页 / 回到前台时查询一次 | 仅用于显示离线提示，不影响任何工具功能 | 同上 |

硬件特性全部 `required="false"`：无磁力计 / 无麦克风 / 无相机的设备走到对应降级分支，不崩溃。

## P6 / P7 新增能力

| 能力 | 真实行为 |
|---|---|
| 转码前台服务 | 选完格式后由 `TranscodeService`（`dataSync`）承载，通知栏显示「已处理 N/M」真实进度；离开页面或切后台继续跑；通知栏「取消」走协作式取消；全部结束自动 `stopSelf()` |
| 倒计时小组件 | Glance 渲染 Room 里最近一个事件与真实剩余天数；15 分钟自刷新 + 数据变更即刷；点击 `omnibox://tool/countdown` 回到工具页 |
| 二维码小组件 | 展示上次扫描/生成结果的首行摘要与「去扫码」入口，无历史时显示引导文案，不编造内容 |
| 开机重建提醒 | `CountdownBootReceiver` 在 `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` 时从 Room 读回 `notifyEnabled` 事件并重新下单闹钟（AlarmManager 的闹钟不跨重启存活） |
| 引擎单测 | 113 条 JVM 用例覆盖密码熵/字符集、单位往返与温度基准、时间戳与时区、摘要标准向量、加权抽样分布、周年边界、dB 换算、采样率计算 |

## P8：探索 · 精选导航

设计稿是一页「探索」外链导航，本次整页落地为独立模块 `feature:explore`，底部导航从 3 Tab 扩为 4 Tab（工具 / 探索 / 常用 / 设置）。

| 能力 | 真实行为 |
|---|---|
| 精选目录 | 30 个站点，覆盖 AI 助手 / AI 图像 / AI 视频 / AI 办公 / 影视 / 音乐 / 二次元 / 阅读 / 游戏 10 个分类；地址入库前逐个 HTTP 探活 |
| 搜索 | 本地即时过滤，命中名称、简介、域名、分类名与标签；无网络也能用 |
| 分类筛选 | Chips 上的数字由注册表实时计算，不写死设计稿演示值 |
| 收藏 | 落本机 DataStore（`omnibox_explore`），卡片星标切换后立即出现在顶部「我的收藏」快捷栏；「最近打开」按真实顺序排列，最长 12 条 |
| 提交站点 | 弹窗输入网址 + 名称 + 分类；`normalizeUrl` 统一补 `https://`、拒绝非 http(s)/无域名输入；重复站点、内置站点直接拒绝并给出原因 |
| 打开方式 | 默认 Chrome Custom Tabs（保留地址栏、证书与安全标识）；关闭开关则走系统默认浏览器；两者都失败时提示「先安装浏览器」 |
| 无痕沙盒 | 打开后不写最近打开记录、任务栈加 `FLAG_ACTIVITY_NO_HISTORY`，并立即清空已有足迹；界面明示「Cookie 与历史仍由浏览器管理」，不谎称能控制浏览器 |
| 站点图标 | `https://<host>/favicon.ico`，4 秒超时 + 256 KB 上限 + 内存/磁盘双缓存；失败退化为首字母头像，绝不显示破图 |
| 离线提示 | 进页与回前台各查一次网络状态，断网时显示提示条，目录仍可浏览 |

**同时修掉的两个「有入口无去处」**

1. 探索页的收藏原先只能点星、没有列表可回看，`favoriteEntries` 是死代码——现在顶部常驻「我的收藏」快捷栏，星标即刻可见可点。
2. 「常用」页的置顶工具链路（Room `favorites` 表 + `HomeEvent.ToggleFavorite`）早就写好了，但界面上一直没有触发点，页面永远是空的——工具卡右侧补上星标按钮，首页与常用页都能切换，空态文案改为指向真实操作。

**与设计稿的刻意差异**

1. 设计稿里指向盗版影视站与影子图书馆（Z-Lib、厂长资源等）的入口未接入，替换为同分类的合规站点（哔哩哔哩、豆瓣、央视网、微信读书、鸠摩搜索等）；
2. 条目标签只写可核验属性（免费 / 免费额度 / 开源 / 需登录），不使用「最强」「神器」类无法证伪的营销词；
3. 「无痕」只承诺本机不留痕，不承诺浏览器侧无痕——Custom Tabs 没有公开的隐私模式开关，写清楚比写得爽重要。

## P9：系统栏适配（edge-to-edge）

`MainActivity` 一直调用 `enableEdgeToEdge()`，targetSdk 35 在 Android 15 上更是强制全屏绘制，但页面从没消费过 WindowInsets——
结果就是顶栏标题压在时间/电量底下、底部内容被手势条挡住。

修法（**只有一处**负责某一侧 inset，避免系统 inset 与组件 padding 叠出双倍空白）：

| 位置 | 做法 |
|---|---|
| 页面根容器（21 处） | `omniStatusBarInset().omniNavBarInset()`，`background()` 写在 inset 之前，状态栏那条区域由页面底色补上 |
| `Scaffold`（4 处） | `contentWindowInsets = OmniScaffoldInsets`，只保留左右安全区，上下交给根容器与底栏 |
| `OmniBottomBar` | 圆角 Surface 内部加 `windowInsetsBottomHeight(navigationBars)`，底色铺到屏幕底边、Tab 本体上移 |
| 状态栏图标 | `SystemBarIconAppearance(darkTheme)` 跟随 **App 主题**而非系统深色开关，避免强制浅色时白底白图标 |

选型说明：没有把 inset 加在 `OmniTopBar` 上。顶栏在多数页面是 `LazyColumn` 的首个 item，加了 inset 只能保证首屏对齐，
滚动后卡片仍会滑到状态栏图标下面；放在根容器上则任何时候都不重叠。

## 新增依赖与体积

| 依赖 | 版本 | 用途 | 是否引入网络权限 |
|---|---|---|---|
| `com.google.zxing:core` | 3.5.3 | 二维码编解码（纯算法） | 否 |
| `androidx.camera:camera-core/camera2/lifecycle/view` | 1.3.4 | 相机预览与帧分析 | 否 |
| `androidx.exifinterface:exifinterface` | 1.3.7 | 读取/校验 EXIF | 否 |
| `androidx.glance:glance-appwidget` | 1.1.1 | 桌面小组件（P6） | 否 |
| `androidx.browser:browser` | 1.8.0 | 探索页 Chrome Custom Tabs 跳转（P8） | 需要 `INTERNET`（已声明，仅探索页使用） |

体积：debug APK 23.2 MB（未混淆、含调试符号），release APK **5.0 MB**（R8 + 资源压缩后约为 debug 的 22%），release AAB 8.8 MB。
**未引入** FFmpeg-Kit、ML Kit、Coil/Glide、HTTP 客户端（OkHttp/Retrofit）：前两者会显著增大包体，图片库与网络栈对本项目没有必要——站点图标用 `HttpURLConnection` + `LruCache` 三十行解决，探索页也没有任何 JSON 接口需要解析。

## 已验证项

| 项 | 结果 |
|---|---|
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL，0 编译错误 0 警告 |
| `./gradlew test` | BUILD SUCCESSFUL，12 个测试类 / 144 条用例 × 2 个变体 = 288 次执行，0 失败 0 错误 0 跳过 |
| `./gradlew :app:assembleRelease :app:bundleRelease` | BUILD SUCCESSFUL，R8 混淆 + 资源压缩生效 |
| `bash scripts/verify-release.sh` | 全部通过：签名 v1/v2/v3、网络权限策略、合并 Manifest 含预期网络权限、哈希留档 |
| `python scripts/check-project.py` | 0 错误 0 警告（19 模块 / 13 条注册 / 106 个编译单元） |
| `python scripts/check-screens.py` | 32 屏 / 1354 条设计文案 / 命中 835，总覆盖率 **61.7%** |
| `aapt dump permissions` | INTERNET、ACCESS_NETWORK_STATE、CAMERA、RECORD_AUDIO、ACCESS_FINE/COARSE_LOCATION、POST_NOTIFICATIONS、VIBRATE、SCHEDULE_EXACT_ALARM、USE_EXACT_ALARM、RECEIVE_BOOT_COMPLETED、FOREGROUND_SERVICE、FOREGROUND_SERVICE_DATA_SYNC、WAKE_LOCK；无 ACCESS_WIFI_STATE 等额外网络权限 |
| release 包内组件 | `CountdownReminderReceiver` / `CountdownBootReceiver` / 两个 Glance `*WidgetReceiver`（带 `android.appwidget.provider` 元数据）/ `TranscodeService`（`foregroundServiceType=dataSync`）均存活 |
| 签名 | `CN=OmniBox, OU=Offline Tool Suite, O=OmniBox, L=Hangzhou, ST=Zhejiang, C=CN`，RSA 4096，有效期 10000 天 |
| debug 产物 | `app-debug.apk` 23,388,168 字节，`CN=Android Debug`，包名 `com.omnibox.debug` |
| release 产物 | `app-release.apk` **4,999,076 字节**（R8 后约为 debug 的 21%）、`app-release.aab` 8,846,685 字节，包名 `com.omnibox` |
| SHA-256（release APK） | `0caa5988ef382cdf2393fb788e46c40f71d5091a79bb23eab8194bb7480b1036` |
| SHA-256（release AAB） | `94c5f0fe917bf8157b7ace71e6df28b84cb557c08409bcd0d096db0b2340cf78` |

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
12. **小组件只有倒计时与二维码两个**：13 款工具中只有这两项在 `supportsWidget=true`，其余工具没有常驻信息可展示，不做空壳小组件。
13. **没有逐屏像素比对**：本机无模拟器/系统镜像，改用 `scripts/check-screens.py` 做中文文案覆盖比对（32 屏 / 1354 条 / 61.7%）。未命中的主要是设计稿里的**营销式说明长句**（如「纯本地沙盒存储 · 零网络流量消耗」），实现里刻意不把这些宣传语搬进 App；短标签类文案基本全中。该指标衡量文案存在性，**不等于**视觉等价。
14. **release 签名用的是本机自建 keystore**：`keystore/omnibox-release.jks` 与 `keystore.properties` 均被 `.gitignore` 排除，口令未入库。上架前必须换成自己的密钥并妥善备份——丢失后无法对已安装用户发布更新。
15. **`exif` 工具 id 由 `exif_clean` 改为 `exif`**：与计划附录 B 对齐，路由同步改为 `tool/exif`。
16. **前台服务的取消是协作式的**：`TranscodeJobRunner` 在每张图片处理前后检查取消标记，正在 `Bitmap.compress` 的单张不会被打断（无法安全中断），因此取消会有最多一张图的延迟。
17. **组合类型转换只覆盖位图**：见偏差 10。

## 工具链修复记录（本轮）

| 问题 | 原因 | 修法 |
|---|---|---|
| 10 个 `.kt` 被自检误报「括号不配对」 | `check-project.py` 用逐条正则剥离注释/字符串，先删 `//` 会破坏字符串里的 URL，导致引号错配 | 改为状态机 `strip_kotlin_literals()`，正确跳过行注释、块注释、三引号原始字符串、`\` 转义与字符字面量 |
| `Preview.setSurfaceProvider` 编译失败 | Kotlin 不能对 Java setter 方法做属性赋值 | 改为显式 `setSurfaceProvider(...)` |
| `Location?` 空安全报错 | 显式声明为可空后未判空 | 类型收紧为 `Location`，靠 `?: return null` 提前返回 |
| `nativeCanvas` / `drawIntoCanvas` 未解析 | 缺少 Compose 扩展导入 | 补 `androidx.compose.ui.graphics.nativeCanvas` 与 `...drawscope.drawIntoCanvas` |
| `@OptIn(ExperimentalGetImage::class)` 无效果告警 | 该注解不是 `@RequiresOptIn` | 改为直接标注 `@ExperimentalGetImage` |

## 下一步（可选增强，非阻塞）

1. 用真机/模拟器逐屏截图，与 32 张 `screen.png` 做像素级比对，补上视觉回归。
2. 云端 CI 首次跑通并出 debug APK；配好 5 个签名 Secrets 后可出 release 包（workflow 已含 `test` 步骤）。
3. 若确需音视频转码，再评估 FFmpeg-Kit（体积 +40~70 MB，需确认无网络权限注入）。
4. 打开 Room `exportSchema=true` 并配置 `room.schemaLocation`，为后续版本迁移留档。
