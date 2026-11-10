# 构建、签名与发布（对应《OmniBox-Android开发计划.md》第 15 章）

## 1. 环境要求

| 项 | 版本 | 本机实际（2026-11-10 已就位） |
|---|---|---|
| JDK | 17（AGP 8.7 / Kotlin 2.0 要求） | Eclipse Temurin **17.0.21+9** @ `E:\Develop\SDK\JDK17` |
| Android SDK | Platform 35 + Build-Tools ≥ 34 | `软件开发\SDK`（Platform 35 r02 / Build-Tools 35.0.0 / Platform-Tools 37.0.1 / cmdline-tools 23.0） |
| Gradle | 8.9（`gradle-wrapper.properties`） | wrapper jar 已随工程提供，首次运行自动下载 8.9 发行版 |
| Kotlin | 2.0.21，Compose Compiler 由 `org.jetbrains.kotlin.plugin.compose` 管理 | — |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 | — |

本机环境变量（**用户级**，新开终端才生效）：`JAVA_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT`，Path 中追加了 `JDK17\bin` 与 `SDK\platform-tools`。工程内还有 `local.properties` 指向 SDK（该文件已被 `.gitignore` 忽略，**不进版本库**；中文路径按 Java Properties 规范写成 `\uXXXX` 转义，避免 ISO-8859-1 读取乱码）。

`python scripts/check-env.py` → 0 阻塞；`python scripts/check-project.py` → 0 错误 0 警告。

### 1.1 JDK 25 能用吗：不能（本工程当前工具链）

**结论：不要用 JDK 25 直接跑 `gradlew`。** 卡点不在 AGP，而在 Gradle 本身：

| 环节 | 本工程锁定版本 | 官方 JDK 要求 / 上限 | JDK 25 结果 |
|---|---|---|---|
| Gradle（daemon 运行） | 8.9 | 支持运行于 Java 8–22；**Java 25 需 Gradle ≥ 9.1.0** | ❌ 启动即失败 |
| AGP | 8.7.3 | 最低且默认 **JDK 17**，最高 API 35 | ❌ 未适配 |
| Kotlin / KSP | 2.0.21 / 2.0.21-1.0.25 | 配套 AGP 8.7，未对 JDK 25 做兼容测试 | ⚠️ 高风险 |
| Java/Kotlin 编译目标 | `JavaVersion.VERSION_17` / `jvmTarget = "17"` | 产物字节码为 Java 17 | 与目标无关 |

JDK 25 的 class 文件主版本号是 69，Gradle 8.9 / Kotlin 2.0 会在解析时出现 `Unsupported class file major version 69` 或 `Could not determine java version from '25'` 一类错误，**在 Sync / 配置阶段就挂掉，根本走不到编译**。

> **常见误区**：`java { toolchain { languageVersion = 17 } }` 或 `kotlin { jvmToolchain(17) }` **救不了**。Toolchain 只决定编译任务用哪个 JDK，**运行 Gradle 的 JVM 仍由 `JAVA_HOME` / `org.gradle.java.home` 决定**。必须让 daemon 跑在 JDK 17 上。

三种可行做法（按推荐排序）：

1. **安装 JDK 17（推荐，工程零改动）**：设 `JAVA_HOME` 指向 JDK 17 后直接 `./gradlew :app:assembleDebug`。Android Studio 自带的 JBR 17/21 也可直接用（Gradle 8.9 支持运行于 21、22，但 AGP 8.7 官方默认 17，取 17 最稳）。
2. **只装 JDK 17 到别的目录、不改系统 `JAVA_HOME`**：在本机用户级配置里指定，不写进仓库：
   ```bash
   # ~/.gradle/gradle.properties（Windows: C:\Users\<你>\.gradle\gradle.properties）
   org.gradle.java.home=C\:\\Java\\jdk-17
   ```
   或单次生效：`JAVA_HOME=/path/to/jdk-17 ./gradlew :app:assembleDebug`（Windows PowerShell：`$env:JAVA_HOME='C:\Java\jdk-17'; .\gradlew.bat :app:assembleDebug`）。
3. **坚持只用 JDK 25 → 必须整套升级**：Gradle ≥ 9.1.0 + AGP 9.x + Kotlin 2.2/2.3 + compileSdk 36/37。AGP 9 有大量破坏性变更（R8 默认重打包类、Build-Tools 36、依赖版本全线上调），与当前锁定的 `libs.versions.toml` 不匹配，**不建议为了迁就 JDK 25 而升级**，成本远高于装一个 JDK 17。

补充：**APK 运行时与构建 JDK 无关**。产物经 D8/R8 转成 dex，跑在设备 ART 上；`minSdk 26` 的手机不会因为用 JDK 17 或 25 编译而受影响。CI 流水线 `.github/workflows/android-build.yml` 已固定为 JDK 17，与本地保持一致。

## 2. Gradle Wrapper（已随工程提供，无需补齐）

`gradle/wrapper/gradle-wrapper.jar`（43,504 字节）由 Gradle 8.9 官方 `wrapper` task 生成，**连同官方 `gradlew` / `gradlew.bat` 一起入库**。`.gitattributes` 强制 `gradlew` 为 LF、`gradlew.bat` 为 CRLF，避免 Linux CI runner 出现 `/bin/sh^M: bad interpreter`。

直接运行即可（Windows 用 `gradlew.bat`）：

```bash
./gradlew --version
./gradlew :app:assembleDebug
```

> 历史说明：工程最初生成时缺这个 jar，旧版文档曾列出 3 种补齐方式，现已全部作废。若将来 jar 意外丢失，用 `gradle wrapper --gradle-version 8.9 --distribution-type bin` 重新生成（需一个含 `settings.gradle` 的空目录，Gradle 不会在没有 build 定义的目录里执行 `wrapper` task）。

## 3. 三条打包命令

```bash
./gradlew :app:assembleDebug      # 调试包：自动 debug 签名，adb 安装自测
./gradlew :app:assembleRelease    # 正式 APK：国内商店 / 侧载
./gradlew :app:bundleRelease      # AAB：Google Play（Play App Signing 再拆分签名）
```

产物：

| 任务 | 路径 |
|---|---|
| debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| release APK | `app/build/outputs/apk/release/app-release.apk` |
| release AAB | `app/build/outputs/bundle/release/app-release.aab` |

图形化也可用 Android Studio 的 **Build → Generate Signed App Bundle / APK**，但建议只用于首次生成密钥，长期固定为 Gradle + CI 保证可复现。

> 注意：未配置签名时 `assembleRelease` 会产出 `app-release-unsigned.apk`，**无法安装**。要可安装的包请用 `assembleDebug`，或在 CI 里配好签名 Secrets 后勾选 `build_release`。

## 4. 签名配置

release 签名从 `keystore.properties` 或环境变量读取，**keystore 与口令绝不入库**（已在 `.gitignore`）。

```bash
# 生成一次，妥善离线备份
keytool -genkeypair -v -keystore signing/omnibox-release.jks \
  -alias omnibox-release -keyalg RSA -keysize 2048 -validity 10000

cp keystore.properties.example keystore.properties   # 填入真实值
```

CI 用 5 个 Secrets：`OMNIBOX_KS_B64`（keystore 文件的 base64 内容）、`OMNIBOX_KS_PASS`、`OMNIBOX_KEY_ALIAS`、`OMNIBOX_KEY_PASS`；第 5 个 `OMNIBOX_KS_PATH` 由 workflow 解码后自动写入，无需手工配置。

- v1/v2/v3 均开启；minSdk 26 理论可关 v1，保留兼容性更稳
- v4（`.idsig` 增量安装）不需要
- 上架 Google Play 用 **upload key** 签 AAB，正式签名密钥由 Play 保管；侧载 APK 与 Play 版签名不同，测试时别混装覆盖
- `versionName` 通过 `BuildConfig.VERSION_DISPLAY` 驱动设置页文案，界面不写死版本号

## 5. release 构建要点

已在 `app/build.gradle.kts` 固化：R8 代码压缩 + 资源收缩、`proguard-rules.pro`（Hilt/Room/协程/JNI/ZXing keep 规则）、ABI 过滤 `arm64-v8a` + `armeabi-v7a`、签名 v1/v2/v3、`assertOfflineManifest` 门禁任务。

## 6. 发布前验证（缺一不可）

```bash
# 1) 合并后的 Manifest 必须查不到 INTERNET（传递依赖注入检查）
M=$(find app/build/intermediates -type f -name AndroidManifest.xml -path '*merged*' | head -1)
grep -i "android.permission.INTERNET" "$M" && echo "FAIL" || echo "OK"

# 2) 签名与证书
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk

# 3) 版本、SDK、非 debuggable
aapt dump badging app/build/outputs/apk/release/app-release.apk | head -20

# 4) 权限清单
aapt dump permissions app/build/outputs/apk/release/app-release.apk

# 5) 留档
shasum -a 256 app/build/outputs/apk/release/app-release.apk

# 6) 飞行模式下安装并跑 32 屏冒烟
adb install -r app/build/outputs/apk/release/app-release.apk
```

`aapt` / `apksigner` 在 `$ANDROID_HOME/build-tools/<版本>/` 下。也可直接运行 `bash scripts/verify-release.sh <apk路径>`。

## 7. 五个容易踩的坑

1. **零 INTERNET 权限可能被依赖偷偷加回**：ML Kit / Firebase 系传递依赖会在 Manifest 合并时注入 `INTERNET`。主 Manifest 已用 `tools:node="remove"` 显式移除，但发布前必须检查合并产物并选 bundled 模型依赖。AGP 8.7 的实际路径是 `app/build/intermediates/merged_manifest/<variant>/process…Manifest/AndroidManifest.xml`（**单数 `merged_manifest`**），用 §6 的 `find -path '*merged*'` 通配最稳。注意 `:verifyNoInternetPermission` 门禁**会正确排除** `tools:node="remove"` 的防御性声明，别把它当成违规删掉。
2. **离线资源必须随包**：Roboto Flex 变量字体、Material Symbols 图标、农历表（1900–2100）、离线汇率快照、WHO 噪声对照表、ML Kit bundled 模型全部进 `assets/res`，禁止运行时下载。设计包的 32 张 `screen.png` 是验收素材，**不要打进 APK**。
3. **NDK `.so` 分包**：若 P2 验证后保留 FFmpeg-Kit / libwebp，用 `splits.abi` 出分 ABI 包；交 AAB 时由 Play 自动拆分。
4. **R8 keep 规则**：Hilt 生成类、Room 实体、kotlinx/ZXing 反射、JNI 方法名都要写进 `proguard-rules.pro`，否则混淆后功能与界面对不上。
5. **多渠道**：国内渠道用 `productFlavors` 每渠道单独构建；**不要在签名后往 APK 里写渠道号**，v2 全文件签名会被破坏。

## 8. GitHub Actions 云构建（零本机 SDK）

`.github/workflows/android-build.yml` 提供完整流水线，**本机可以不装 Android SDK**。runner 是 `ubuntu-latest`，`android-actions/setup-android@v3` 会自动安装 Platform 35 / Build-Tools 35.0.0 / platform-tools。

### 触发方式

| 事件 | 产物 |
|---|---|
| `push` 到 `main`/`master`、`pull_request` | debug APK |
| 手动 `workflow_dispatch`（默认） | debug APK |
| `workflow_dispatch` 勾选 `build_release` | debug APK + **签名 release APK** |
| `workflow_dispatch` 勾选 `build_aab` | 上述 + AAB |

### 首次使用步骤

1. 把 `OmniBox/` 推到 GitHub 仓库（`local.properties` 已被忽略，不会带上去）
2. Actions → **OmniBox Android Build** → Run workflow，直接跑默认配置
3. 构建结束后在 run 页面下载 `omnibox-debug-apk` 工件，解压后 `adb install app-debug.apk` 即可装到手机

**默认产物是 debug 包，自动带 debug 签名，不需要任何 Secrets 就能出可安装的 APK。**

### 签名 release 包（可选）

在仓库 `Settings → Secrets and variables → Actions` 添加 5 个 Secrets，然后手动触发时勾选 `build_release`：

| Secret | 值 |
|---|---|
| `OMNIBOX_KS_B64` | keystore 的纯 base64：`base64 -w0 omnibox-release.jks`（PowerShell：`[Convert]::ToBase64String([IO.File]::ReadAllBytes('…jks'))`），**不要带换行、不要 `data:` 前缀** |
| `OMNIBOX_KS_PASS` | keystore 口令 |
| `OMNIBOX_KEY_ALIAS` | key alias |
| `OMNIBOX_KEY_PASS` | key 口令 |

workflow 会把 `OMNIBOX_KS_B64` 解码到 `$RUNNER_TEMP/omnibox-release.jks` 并写入 `OMNIBOX_KS_PATH`；`app/build.gradle.kts` 从这个环境变量读取 `storeFile`。任一缺失会 `::error::` 明确失败，而不是悄悄产出 unsigned 包。

### CI 里已固化的门禁

- `python3 scripts/check-project.py` 静态自检
- `:verifyNoInternetPermission` + `:app:assertOfflineManifest` 源码级零网络权限
- 构建后扫描 `merged*` 的 `AndroidManifest.xml`，发现 `INTERNET` 即失败
- `apksigner verify --verbose --print-certs` + SHA-256 留档
- 失败时上传 `**/build/reports/**` 便于排查编译错误
- `gradle/actions/setup-gradle@v4` 缓存依赖；`concurrency` 取消重复构建；45 分钟超时

> 注意：workflow **不要覆盖 `ANDROID_HOME`**，setup-android 会自行把 SDK 装到 runner 预置路径并写入环境变量。

## 9. 本机 SDK 安装记录与已知坑

安装目录：`软件开发\SDK`（标准布局）

```
SDK/
├─ cmdline-tools/latest/   # commandlinetools-win-16111833_latest.zip，SHA1 57d04f2d…
├─ platforms/android-35/   # platform-35_r02.zip，SHA1 0bb560a9…
├─ build-tools/35.0.0/     # build-tools_r35_windows.zip，SHA1 af059bb6…
├─ platform-tools/         # 37.0.1（sdkmanager 安装）
└─ licenses/               # SDK 许可已接受
```

- 官方源 `https://dl.google.com/android/repository/` 实测直连可用（HTTP 200、支持 Range、约 3 MB/s）；清华/腾讯云的 AndroidSDK 镜像路径不可用，华为云镜像只有老版本包，**都不如官方源全**。
- 官方仓库清单：`https://dl.google.com/android/repository/repository2-3.xml`，包名、大小、SHA1 都以它为准。
- **cmdline-tools 23.0 的 `sdkmanager.bat` 有坑**：包名里的分号 `platforms;android-35` 会被 Windows 批处理当参数分隔符拆成两个参数，报 `Package platforms not found` / `Package android-35 not found`，且退出码仍是 0。不带分号的 `platform-tools` 能正常装。绕过办法是下载官方 zip 按标准布局落地，或用新版 `android sdk install` CLI。
- zip 顶层目录名：`platform-35_r02.zip` → `android-35`；`build-tools_r35_windows.zip` → **`android-15`**（API 35 的内部代号），必须重命名为 `build-tools/35.0.0`，AGP 按目录名选版本。
- 该版本 sdkmanager 已标记 deprecated，提示改用 Android CLI；不影响已落地组件的可用性。
