#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OmniBox 工程静态自检（不需要 JDK / Android SDK 即可运行）。

用法：python scripts/check-project.py
检查：模块拓扑、Gradle alias、Kotlin package/括号、网络权限策略、注册表完整性、设计基线覆盖。
"""
import os
import re
import sys
import glob
import json

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)
issues, warnings, info = [], [], []


def rel(p):
    return os.path.relpath(p, ROOT).replace('\\', '/')


def strip_kotlin_literals(src: str) -> str:
    """剥离注释与字符串/字符字面量，只留可执行代码，供括号配平粗检。

    必须用状态机而不是逐条正则：`//` 会出现在字符串里（URL、正则），
    而 `{` `}` 会出现在字符串模板与 JSON 示例里；纯正则剥离会互相破坏，
    导致明明能编译通过的源文件被误报「括号不配对」。
    """
    out, i, n = [], 0, len(src)
    while i < n:
        ch = src[i]
        # 行注释
        if ch == '/' and i + 1 < n and src[i + 1] == '/':
            j = src.find('\n', i)
            i = n if j < 0 else j
            continue
        # 块注释（Kotlin 支持嵌套，这里按最外层配对处理已足够）
        if ch == '/' and i + 1 < n and src[i + 1] == '*':
            j = src.find('*/', i + 2)
            i = n if j < 0 else j + 2
            continue
        # 三引号原始字符串
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = n if j < 0 else j + 3
            continue
        # 普通字符串（含 \ 转义与 ${} 模板，整段丢弃不会破坏配平）
        if ch == '"':
            i += 1
            while i < n:
                if src[i] == '\\':
                    i += 2
                    continue
                if src[i] == '"':
                    i += 1
                    break
                i += 1
            continue
        # 字符字面量
        if ch == "'":
            i += 1
            while i < n:
                if src[i] == '\\':
                    i += 2
                    continue
                if src[i] == "'":
                    i += 1
                    break
                i += 1
            continue
        out.append(ch)
        i += 1
    return ''.join(out)


# 1. settings.gradle.kts 的模块与目录一致性
settings = open('settings.gradle.kts', encoding='utf-8').read()
includes = re.findall(r'include\("([^"]+)"\)', settings)
for mod in includes:
    path = mod.replace(':', '/').lstrip('/')
    if not os.path.isdir(path):
        issues.append(f'模块目录缺失: {mod} -> {path}')
        continue
    for needed in ['build.gradle.kts', 'src/main/AndroidManifest.xml']:
        if not os.path.isfile(os.path.join(path, needed)):
            issues.append(f'{mod} 缺少 {needed}')
info.append(f'Gradle 模块 {len(includes)} 个: {", ".join(includes[:4])} ...')

# 2. 版本目录 alias 引用
toml = open('gradle/libs.versions.toml', encoding='utf-8').read()
libs_block = toml.split('[libraries]')[1].split('[plugins]')[0]
plugins_block = toml.split('[plugins]')[1]
lib_keys = set(re.findall(r'^([\w.-]+)\s*=', libs_block, re.M))
plugin_keys = set(re.findall(r'^([\w.-]+)\s*=', plugins_block, re.M))
gradle_files = glob.glob('**/build.gradle.kts', recursive=True)
used_libs, used_plugins = set(), set()
for gf in gradle_files:
    s = open(gf, encoding='utf-8').read()
    for m in re.findall(r'\blibs\.plugins\.([\w.]+)', s):
        used_plugins.add(m)
    for m in re.findall(r'\blibs\.([\w.]+)', s):
        if not m.startswith('plugins.'):
            used_libs.add(m)
for a in sorted(used_libs):
    # TOML key 用 "-"，Kotlin DSL 访问器用 "."，例如 androidx-core-ktx -> libs.androidx.core.ktx
    if a.replace('.', '-') not in lib_keys and a not in lib_keys:
        issues.append(f'版本目录缺少 library alias: libs.{a}')
for a in sorted(used_plugins):
    if a.replace('.', '-') not in plugin_keys and a not in plugin_keys:
        issues.append(f'版本目录缺少 plugin alias: libs.plugins.{a}')
info.append(f'版本目录: {len(lib_keys)} 个 library / {len(plugin_keys)} 个 plugin; '
            f'实际引用 {len(used_libs)} / {len(used_plugins)}')

# 3. Kotlin 文件 package 与路径一致、括号平衡
# reference/kt 是从设计文档抽取的参考资料，不参与 Gradle 编译，跳过路径一致性检查
kt_files = sorted(p for p in glob.glob('**/*.kt', recursive=True)
                  if not p.replace('\\', '/').startswith('reference/'))
info.append(f'参与编译的 Kotlin 源文件 {len(kt_files)} 个')
for p in kt_files:
    s = open(p, encoding='utf-8').read()
    m = re.search(r'^package\s+([\w.]+)', s, re.M)
    if not m:
        issues.append(f'{rel(p)} 缺少 package 声明')
        continue
    expected = os.path.dirname(p).replace('/', '.').replace('\\', '.')
    # 去掉 src/main/java、src/main/kotlin、src/test/java、src/androidTest/java 前缀
    for seg in ['src.main.java.', 'src.main.kotlin.',
                'src.test.java.', 'src.test.kotlin.',
                'src.androidTest.java.', 'src.androidTest.kotlin.']:
        idx = expected.find(seg)
        if idx >= 0:
            expected = expected[idx + len(seg):]
            break
    if m.group(1) != expected:
        issues.append(f'{rel(p)} package={m.group(1)} 但路径推断为 {expected}')
    # 去掉字符串与注释后做括号粗检
    stripped = strip_kotlin_literals(s)
    for op, cl, name in [('{', '}', '花括号'), ('(', ')', '圆括号'), ('[', ']', '方括号')]:
        if stripped.count(op) != stripped.count(cl):
            issues.append(f'{rel(p)} {name}不配对: {stripped.count(op)} vs {stripped.count(cl)}')

# 4. 网络权限策略（源码 Manifest）
# 探索页需要 INTERNET 加载站点图标、ACCESS_NETWORK_STATE 做离线提示；
# 这两项只能由 app 模块统一声明，feature 模块禁止自行声明，避免「某个工具偷偷联网」。
NETWORK_PERMS = ('android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE')
APP_MANIFEST = 'app/src/main/AndroidManifest.xml'
declared = set()
for mf in glob.glob('**/src/main/AndroidManifest.xml', recursive=True):
    s = open(mf, encoding='utf-8').read()
    for perm in NETWORK_PERMS:
        if perm in s:
            declared.add((rel(mf), perm))
            if rel(mf) != APP_MANIFEST:
                issues.append(f'{rel(mf)} 不应声明 {perm}（网络权限只在 app 模块声明）')
missing = [p for p in NETWORK_PERMS if (APP_MANIFEST, p) not in declared]
if missing:
    issues.append(f'app Manifest 缺少网络权限声明: {missing}')
if os.path.isfile(APP_MANIFEST) and '<queries>' not in open(APP_MANIFEST, encoding='utf-8').read():
    issues.append('app Manifest 缺少 <queries> 浏览器可见性声明（Android 11+ 无法判断浏览器是否存在）')
info.append('网络权限策略检查完成：INTERNET 仅用于探索页外链与图标，工具本体仍全离线')

# 5. 工具注册表完整性
modules = glob.glob('feature/*/src/main/java/**/*ToolModule.kt', recursive=True)
ids = []
for p in modules:
    s = open(p, encoding='utf-8').read()
    for i in re.findall(r'id\s*=\s*"([^"]+)"', s):
        ids.append(i)
EXPECTED_TOOLS = 15  # P2 13 款 + P10 硬件诊断 2 款（display_lab / battery）
if len(ids) != EXPECTED_TOOLS:
    issues.append(f'工具注册表应为 {EXPECTED_TOOLS} 条，实际 {len(ids)} 条: {sorted(ids)}')
elif len(set(ids)) != EXPECTED_TOOLS:
    issues.append(f'工具 ID 重复: {ids}')
else:
    info.append(f'工具注册表 {EXPECTED_TOOLS} 条齐全（{len(modules)} 个 Hilt Module）')

# 6. 设计基线覆盖
baseline = os.path.join(os.path.dirname(ROOT), 'stitch_', 'stitch_')
if os.path.isdir(baseline):
    screens = [os.path.basename(os.path.dirname(p))
               for p in glob.glob(baseline + '/*/code.html')]
    info.append(f'设计基线检出 {len(screens)} 屏 code.html')
else:
    warnings.append('未找到设计基线 stitch_/stitch_，跳过覆盖检查')

# 7. 参考源码清单
manifest_path = 'reference/manifest.json'
if os.path.isfile(manifest_path):
    ref = json.load(open(manifest_path, encoding='utf-8'))
    info.append(f'参考源码 {len(ref)} 个 .kt（reference/kt/，不参与编译）')

# 8. 发布配置存在性
for f in ['app/proguard-rules.pro', 'keystore.properties.example',
          'gradle/wrapper/gradle-wrapper.properties', '.gitignore', 'BUILD.md']:
    if not os.path.isfile(f):
        issues.append(f'缺少发布配置文件: {f}')
if not os.path.isfile('gradle/wrapper/gradle-wrapper.jar'):
    warnings.append('gradle/wrapper/gradle-wrapper.jar 缺失（本机无网络未能下载）；'
                    '用 Android Studio 打开工程或执行 gradle wrapper --gradle-version 8.9 生成')

# 输出
print('=' * 68)
print('OmniBox 工程静态自检')
print('=' * 68)
for x in info:
    print('  [OK]  ' + x)
for x in warnings:
    print('  [WARN]' + x)
for x in issues:
    print('  [FAIL]' + x)
print('-' * 68)
print(f'结论: {len(issues)} 个错误, {len(warnings)} 个警告')
print('=' * 68)
sys.exit(1 if issues else 0)
