#!/usr/bin/env python3
"""OmniBox 构建环境自检（只读，不修改任何文件）。

检查项：
  - 运行 Gradle 的 JVM 版本（JAVA_HOME / PATH 中的 java）
  - org.gradle.java.home 覆盖值
  - Android SDK / Platform 35 / Build-Tools
  - gradle-wrapper.jar 是否补齐

本工程锁定 Gradle 8.9 + AGP 8.7.3 + Kotlin 2.0.21，要求运行 Gradle 的 JVM 为 JDK 17。
JDK 21/22 可运行 Gradle 但非 AGP 官方默认；JDK 23+ 不支持，JDK 25 需 Gradle >= 9.1.0。
详见 BUILD.md 1.1。
"""
import os
import re
import subprocess
import sys
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8")
except (AttributeError, OSError):
    pass

ROOT = Path(__file__).resolve().parent.parent
OK, WARN, BAD, INFO = "[OK]  ", "[WARN]", "[FAIL]", "       "


def java_version(executable="java"):
    """返回 (major:int|None, raw:str)。java -version 输出到 stderr。"""
    try:
        p = subprocess.run(
            [executable, "-version"], capture_output=True, text=True,
            timeout=15, encoding="utf-8", errors="replace",
        )
    except (OSError, subprocess.SubprocessError) as e:
        return None, f"无法执行: {e}"
    raw = (p.stderr or p.stdout or "").strip().splitlines()
    first = raw[0] if raw else "(无输出)"
    m = re.search(r'version\s+"([^"]+)"', first)
    if not m:
        return None, first
    v = m.group(1)
    parts = v.split(".")
    major = int(parts[0]) if parts[0].isdigit() else None
    if major == 1 and len(parts) > 1 and parts[1].isdigit():
        major = int(parts[1])  # 1.8 -> 8
    return major, v


def find_java_home_java(home):
    if not home:
        return None
    exe = Path(home) / "bin" / ("java.exe" if os.name == "nt" else "java")
    return str(exe) if exe.is_file() else None


def read_gradle_java_home(path):
    if not path.is_file():
        return None
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        if k.strip() == "org.gradle.java.home":
            return v.strip()
    return None


def main():
    print("=" * 68)
    print("OmniBox 构建环境自检（Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21）")
    print("=" * 68)

    issues, warnings = [], []

    # 1) JAVA_HOME
    java_home = os.environ.get("JAVA_HOME", "").strip('"')
    home_java = find_java_home_java(java_home)
    if java_home and home_java:
        major, raw = java_version(home_java)
        verdict = OK if major == 17 else (WARN if major in (21, 22) else BAD)
        print(f"{verdict} JAVA_HOME = {java_home}")
        print(f"{INFO}JDK 版本 = {raw} (major {major})")
        if major == 17:
            pass
        elif major in (21, 22):
            warnings.append(f"JAVA_HOME 是 JDK {major}：Gradle 8.9 可运行，但 AGP 8.7.3 官方默认 JDK 17，建议改 17")
        elif major and major >= 23:
            issues.append(f"JAVA_HOME 是 JDK {major}，Gradle 8.9 无法在其上运行（Java 25 需 Gradle >= 9.1.0）")
        else:
            issues.append(f"JAVA_HOME 指向的 JDK 版本无法识别（{raw}）")
    elif java_home:
        issues.append(f"JAVA_HOME={java_home} 但找不到 bin/java，请检查路径")
        print(f"{BAD} JAVA_HOME = {java_home}（bin/java 不存在）")
    else:
        print(f"{WARN} JAVA_HOME 未设置；Gradle 将使用 PATH 中的 java")

    # 2) PATH 中的 java
    pmajor, praw = java_version("java")
    if pmajor is None:
        print(f"{BAD} PATH 中的 java 不可用：{praw}")
        issues.append("未检测到可用的 java 命令，请安装 JDK 17")
    else:
        print(f"{INFO}PATH 中 java = {praw} (major {pmajor})")

    # 3) org.gradle.java.home（优先级高于 JAVA_HOME）
    override = read_gradle_java_home(ROOT / "gradle.properties")
    user_props = Path.home() / ".gradle" / "gradle.properties"
    override_user = read_gradle_java_home(user_props)
    effective_override = override or override_user
    if effective_override:
        src = "工程 gradle.properties" if override else str(user_props)
        print(f"{INFO}org.gradle.java.home 覆盖值 = {effective_override}（来自 {src}）")
        exe = find_java_home_java(effective_override.replace("\\:", ":").replace("\\\\", "\\"))
        if exe:
            m, r = java_version(exe)
            print(f"{INFO}覆盖值指向 JDK {r} (major {m})" + ("  <- Gradle daemon 实际会用这个" if m else ""))
            if m != 17:
                issues.append(f"org.gradle.java.home 指向 JDK {m}，应改为 JDK 17")
        else:
            issues.append(f"org.gradle.java.home 路径无效：{effective_override}")
    else:
        print(f"{INFO}未设置 org.gradle.java.home（用 JAVA_HOME / PATH 的 java）")

    # 4) Android SDK
    sdk = (os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "").strip('"')
    local_props = ROOT / "local.properties"
    if not sdk and local_props.is_file():
        for line in local_props.read_text(encoding="utf-8", errors="replace").splitlines():
            if line.startswith("sdk.dir="):
                sdk = line.split("=", 1)[1].strip().replace("\\\\", "\\").replace("\\:", ":")
                break
    if not sdk:
        warnings.append("未检测到 Android SDK（ANDROID_HOME / ANDROID_SDK_ROOT / local.properties 均未设置）")
        print(f"{WARN} Android SDK 未配置")
    else:
        sdkp = Path(sdk)
        print(f"{INFO}Android SDK = {sdkp}" + ("" if sdkp.is_dir() else "（目录不存在）"))
        if not sdkp.is_dir():
            issues.append(f"Android SDK 目录不存在：{sdkp}")
        else:
            plat = sdkp / "platforms" / "android-35"
            print(f"{OK}   Platform 35 就位" if plat.is_dir() else f"{BAD} 缺少 platforms/android-35（compileSdk 35 需要）")
            if not plat.is_dir():
                issues.append("缺少 Android SDK Platform 35")
            bt = sdkp / "build-tools"
            versions = sorted(p.name for p in bt.iterdir() if p.is_dir()) if bt.is_dir() else []
            print(f"{OK}   Build-Tools: {', '.join(versions[-3:])}" if versions else f"{BAD} 未安装 Build-Tools（需 >= 34.0.0）")
            if not versions:
                issues.append("缺少 SDK Build-Tools")

    # 5) gradle-wrapper.jar
    jar = ROOT / "gradle" / "wrapper" / "gradle-wrapper.jar"
    if jar.is_file():
        print(f"{OK}   gradle-wrapper.jar 已就位 ({jar.stat().st_size} bytes)")
    else:
        warnings.append("gradle/wrapper/gradle-wrapper.jar 缺失，见 BUILD.md 第 2 节")
        print(f"{WARN} gradle-wrapper.jar 缺失（BUILD.md 第 2 节有 3 种补齐方式）")

    # 结论
    print("-" * 68)
    if issues:
        print(f"结论：环境不满足要求，共 {len(issues)} 项阻塞")
        for i, x in enumerate(issues, 1):
            print(f"  {i}. {x}")
        print()
        print("推荐修复：安装 JDK 17 并把 JAVA_HOME 指向它，或在 ~/.gradle/gradle.properties 写")
        print("          org.gradle.java.home=<JDK 17 路径>")
        print("注意：Java Toolchain 不能替代此设置——它只影响编译任务，不影响运行 Gradle 的 JVM。")
        print("详细说明见 BUILD.md 1.1。")
        return 1
    print("结论：无阻塞项，可执行 ./gradlew :app:assembleDebug")
    for w in warnings:
        print(f"  {WARN} {w}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
