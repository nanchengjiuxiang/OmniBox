#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""OmniBox 设计稿中文文案覆盖率检查（纯 Python 3 标准库）。

用途
----
本机没有 Android 模拟器 / 系统镜像，无法做逐屏像素比对。本脚本改为从
Google Stitch 导出的静态 HTML 中提取「可见中文文案」，再在 Kotlin/Compose
源码中查找同名文案，输出可复现的覆盖率统计与 Markdown 报告。

用法
----
    cd OmniBox
    python scripts/check-screens.py
    python scripts/check-screens.py --design-dir "E:/path/to/stitch_/stitch_"

退出码
------
    0  总覆盖率 >= 80%
    1  总覆盖率 < 80%（报告仍会完整写出）
    2  参数 / 目录 / 源文件等前置条件错误

只读设计稿与 Kotlin 源码，只写 SCREEN_COVERAGE.md，不修改工程源码。
"""

from __future__ import annotations

import argparse
import html
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional, Sequence, Tuple

# ---------------------------------------------------------------------------
# 路径与统计口径
# ---------------------------------------------------------------------------

PROJECT_ROOT = Path(__file__).resolve().parent.parent
REPORT_PATH = PROJECT_ROOT / "SCREEN_COVERAGE.md"

SOURCE_DIRS = ("app", "core", "feature")
PASS_THRESHOLD = 80.0

# 计划已裁定不实现的旧版首页副本。目录内仍有 code.html，但显式排除。
KNOWN_EXCLUDED = {
    "omnibox_offline_utility_suite": (
        "旧版首页副本；计划已裁定不实现，故不纳入实现覆盖率统计。"
    ),
}

# 源码侧排除目录：reference/ 为参考资料，build/ 为构建产物，
# src/test/ 与 src/androidTest/ 为测试代码。
SOURCE_EXCLUDED_DIRS = {"build", "reference", "test", "androidtest"}

# ---------------------------------------------------------------------------
# HTML 提取规则
# ---------------------------------------------------------------------------

CJK_RE = re.compile(r"[\u4e00-\u9fff]")
WS_RE = re.compile(r"\s+")

BODY_RE = re.compile(r"(?is)<body\b[^>]*>(.*?)</body\s*>")
HEAD_RE = re.compile(r"(?is)<head\b[^>]*>.*?</head\s*>")
SCRIPT_RE = re.compile(r"(?is)<script\b[^>]*>.*?</script\s*>")
STYLE_RE = re.compile(r"(?is)<style\b[^>]*>.*?</style\s*>")
COMMENT_RE = re.compile(r"(?s)<!--.*?-->")

# Material Symbols / Material Icons 是字体连字，文本节点里会留下
# “search”“more_vert”“offline_pin”等英文图标名。它们不是界面文案，
# 且会和中文标签粘在同一行，必须在抽文案前整段删掉。
ICON_RE = re.compile(
    r"""(?is)<(?:span|i)\b[^>]*\bclass\s*=\s*
        (?:"[^"]*material-(?:symbols|icons)[^"]*"|'[^']*material-(?:symbols|icons)[^']*')
        [^>]*>.*?</(?:span|i)\s*>""",
    re.VERBOSE,
)

# 块级标签换成换行后再剥标签，避免把相邻两个文案粘成一条。
# span 等行内标签只换成空格，这样“剩余 <span>12</span> 天”仍会保持为一行。
BLOCK_TAG_RE = re.compile(
    r"(?i)</?(?:address|article|aside|blockquote|br|caption|dd|details|div|dl|dt|"
    r"fieldset|figcaption|figure|footer|form|h[1-6]|header|hr|li|main|nav|ol|p|pre|"
    r"section|summary|table|tbody|td|tfoot|th|thead|tr|ul|button|label|option|textarea)\b[^>]*>"
)
TAG_RE = re.compile(r"(?s)<[^>]*>")

# 明显占位符：不计入设计文案。本次样例中基本不会命中，仍保留规则并统计移除数。
PLACEHOLDER_RE = re.compile(
    r"(?i)\b(?:lorem|ipsum|todo|tbd|placeholder)\b|占位符|待补充|示例文本"
)

# 纯数字 / 纯单位 / 纯符号。由于本脚本只收含 CJK 的行，这条主要是自文档化，
# 防止后续修改正则时把纯数字也算成文案。
TRIVIAL_RE = re.compile(
    r"^[\s\d%‰°℃.,，。:：+\-–—/\\|()（）\[\]【】·•…]+$"
)

# ---------------------------------------------------------------------------
# 匹配规则
# ---------------------------------------------------------------------------

# 格式化占位符：%d、%02d、%1$s、%.2f，以及 Kotlin 字符串模板 ${...}。
# 先去掉它们，才能让设计稿「剩余 12 天」匹配源码 "剩余 %d 天"。
FORMAT_SPEC_RE = re.compile(
    r"%(?:\d+\$)?[-+ 0#]*\d*(?:\.\d+)?[a-zA-Z]|\$\{[^{}]*\}"
)

# 宽松骨架只保留 CJK 与拉丁字母，数字、标点、空白全部去掉。
# 注意：数字必须在设计稿一侧就去掉，否则「全部 (13)」会变成「全部13」，
# 无法匹配源码里只有「全部」的标签。
NON_LETTER_CJK_RE = re.compile(r"[^A-Za-z\u4e00-\u9fff]")
NON_CJK_RE = re.compile(r"[^\u4e00-\u9fff]")

MATCH_STRICT = "strict"
MATCH_LOOSE = "loose"
MATCH_CJK = "cjk"


@dataclass
class ScreenResult:
    screen_id: str
    items: List[str]
    hits: int = 0
    strict_hits: int = 0
    loose_hits: int = 0
    cjk_hits: int = 0
    misses: List[str] = field(default_factory=list)
    placeholders_removed: int = 0

    @property
    def total(self) -> int:
        return len(self.items)

    @property
    def rate(self) -> float:
        return (100.0 * self.hits / self.total) if self.total else 0.0


# ---------------------------------------------------------------------------
# 通用工具
# ---------------------------------------------------------------------------


def display_path(path: Path) -> str:
    return str(path).replace("\\", "/")


def natural_key(name: str) -> List[Tuple[int, object]]:
    """让 _1、_2、_10 按数字顺序排列，而不是字典序。"""
    return [
        (0, int(part)) if part.isdigit() else (1, part.lower())
        for part in re.split(r"(\d+)", name)
    ]


def read_utf8(path: Path) -> str:
    """设计稿/源码均按 UTF-8 读取；异常字节替换，避免整份文件读取失败。"""
    return path.read_text(encoding="utf-8", errors="replace")


def resolve_design_dir(cli_value: Optional[str]) -> Optional[Path]:
    if cli_value:
        candidate = Path(cli_value).expanduser()
        if not candidate.is_absolute():
            candidate = Path.cwd() / candidate
        return candidate.resolve()

    # 默认从工程根目录向上找 ../stitch_/stitch_。
    # 同时容忍从 scripts/ 或工程内其他子目录运行的场景。
    candidates = [
        PROJECT_ROOT.parent / "stitch_" / "stitch_",
        Path.cwd().parent / "stitch_" / "stitch_",
        Path.cwd() / "stitch_" / "stitch_",
    ]
    seen = set()
    for candidate in candidates:
        resolved = candidate.resolve()
        if resolved in seen:
            continue
        seen.add(resolved)
        if resolved.is_dir():
            return resolved
    return None


def fail(message: str) -> int:
    print("[错误] " + message, file=sys.stderr)
    return 2


# ---------------------------------------------------------------------------
# 设计稿文案提取
# ---------------------------------------------------------------------------


def extract_design_texts(html_path: Path) -> Tuple[List[str], int]:
    """返回（去重后的文案列表，被占位符规则剔除的条数）。

    提取步骤（与 SCREEN_COVERAGE.md 的方法学保持一致）：
    1. 只取 <body> 内文本；没有 body 则去掉 <head> 后处理全文。
    2. 删除 HTML 注释、<script>、<style>、Material 图标字体元素。
    3. 块级标签替换为换行，其余标签替换为空格，再 html.unescape。
    4. 按行折叠空白；只保留含 CJK 的行。
    5. 剔除长度 < 2、纯数字/符号/单位、明显占位符；同屏内去重。
    """
    raw = read_utf8(html_path)

    body_match = BODY_RE.search(raw)
    scoped = body_match.group(1) if body_match else HEAD_RE.sub(" ", raw)

    scoped = COMMENT_RE.sub(" ", scoped)
    scoped = SCRIPT_RE.sub(" ", scoped)
    scoped = STYLE_RE.sub(" ", scoped)
    scoped = ICON_RE.sub(" ", scoped)
    scoped = BLOCK_TAG_RE.sub("\n", scoped)
    scoped = TAG_RE.sub(" ", scoped)
    text = html.unescape(scoped)

    result: List[str] = []
    seen = set()
    placeholders_removed = 0

    for line in text.splitlines():
        normalized = WS_RE.sub(" ", line).strip()
        if not normalized or len(normalized) < 2:
            continue
        if not CJK_RE.search(normalized):
            continue  # 纯数字、纯英文、纯符号、图标连字名都会被这里过滤。
        if TRIVIAL_RE.fullmatch(normalized):
            continue
        if PLACEHOLDER_RE.search(normalized):
            placeholders_removed += 1
            continue
        if normalized in seen:
            continue
        seen.add(normalized)
        result.append(normalized)

    return result, placeholders_removed


# ---------------------------------------------------------------------------
# 源码索引与匹配
# ---------------------------------------------------------------------------


def collect_kotlin_files() -> List[Path]:
    files: List[Path] = []
    for base_name in SOURCE_DIRS:
        base = PROJECT_ROOT / base_name
        if not base.is_dir():
            continue
        for path in base.rglob("*.kt"):
            try:
                rel_parts = [part.lower() for part in path.relative_to(PROJECT_ROOT).parts]
            except ValueError:
                continue
            if any(part in SOURCE_EXCLUDED_DIRS for part in rel_parts):
                continue
            files.append(path)
    return sorted(files, key=lambda p: display_path(p))


def skeleton(text: str) -> str:
    """去格式化符、数字、标点与空白，只留字母和 CJK，并转小写。"""
    stripped = FORMAT_SPEC_RE.sub("", text)
    return NON_LETTER_CJK_RE.sub("", stripped).lower()


def cjk_only(text: str) -> str:
    return NON_CJK_RE.sub("", text)


def classify(text: str, source_flat: str, source_skeleton: str, source_cjk: str) -> Optional[str]:
    """返回命中层级；None 表示未命中。

    1. strict：设计文案（空白折叠后）是源码原文的子串。
    2. loose：两边都去掉数字、标点、格式化符与空白后，骨架互为子串。
       这是需求指定的“宽松匹配”，用于 "剩余 %d 天" ↔「剩余 12 天」。
    3. cjk：纯中文骨架兜底，处理 Kotlin 字符串模板/英文缩写夹在中间的情况；
       为避免 2 字短词误命中，要求至少 4 个汉字。
    """
    if text in source_flat:
        return MATCH_STRICT

    sk = skeleton(text)
    if sk and sk in source_skeleton:
        return MATCH_LOOSE

    cjk = cjk_only(text)
    if len(cjk) >= 4 and cjk in source_cjk:
        return MATCH_CJK

    return None


def scan(
    design_dir: Path,
    source_flat: str,
    source_skeleton: str,
    source_cjk: str,
) -> Tuple[List[ScreenResult], int, List[str], List[str]]:
    """扫描设计稿目录并匹配。

    返回（纳入统计的屏结果，含 code.html 的目录总数，被排除目录名，无 code.html 目录名）。
    """
    code_dirs = sorted(
        [
            child
            for child in design_dir.iterdir()
            if child.is_dir() and (child / "code.html").is_file()
        ],
        key=lambda p: natural_key(p.name),
    )

    ignored_dirs: List[str] = []
    non_code_dirs: List[str] = []
    for child in sorted([p for p in design_dir.iterdir() if p.is_dir()], key=lambda p: natural_key(p.name)):
        if not (child / "code.html").is_file():
            non_code_dirs.append(child.name)

    results: List[ScreenResult] = []
    for screen_dir in code_dirs:
        if screen_dir.name in KNOWN_EXCLUDED:
            ignored_dirs.append(screen_dir.name)
            continue

        items, placeholders_removed = extract_design_texts(screen_dir / "code.html")
        result = ScreenResult(
            screen_id=screen_dir.name,
            items=items,
            placeholders_removed=placeholders_removed,
        )

        for text in items:
            kind = classify(text, source_flat, source_skeleton, source_cjk)
            if kind is None:
                result.misses.append(text)
                continue
            result.hits += 1
            if kind == MATCH_STRICT:
                result.strict_hits += 1
            elif kind == MATCH_LOOSE:
                result.loose_hits += 1
            else:
                result.cjk_hits += 1

        results.append(result)

    return results, len(code_dirs), ignored_dirs, non_code_dirs


# ---------------------------------------------------------------------------
# 输出
# ---------------------------------------------------------------------------


def format_console(results: Sequence[ScreenResult], total_hits: int, total_items: int) -> List[str]:
    lines: List[str] = []
    for result in results:
        lines.append(
            f"{result.screen_id}  {result.rate:6.1f}%  {result.hits}/{result.total}"
        )
    overall = (100.0 * total_hits / total_items) if total_items else 0.0
    lines.append(f"{'总计'}  {overall:6.1f}%  {total_hits}/{total_items}")
    return lines


def md_escape(text: str) -> str:
    return text.replace("\\", "\\\\").replace("|", "\\|").replace("\n", " ").strip()


def truncate(text: str, limit: int = 56) -> str:
    return text if len(text) <= limit else text[:limit] + "…"


def format_misses(misses: Sequence[str], limit: int = 5) -> str:
    if not misses:
        return "—"
    shown = "、".join(md_escape(truncate(item)) for item in misses[:limit])
    if len(misses) > limit:
        shown += f" …（共 {len(misses)} 条未命中）"
    return shown


def build_report(
    *,
    design_dir: Path,
    screen_count: int,
    code_dir_count: int,
    ignored_dirs: Sequence[str],
    non_code_dirs: Sequence[str],
    kotlin_count: int,
    results: Sequence[ScreenResult],
    total_hits: int,
    total_items: int,
    strict_hits: int,
    loose_hits: int,
    cjk_hits: int,
    placeholders_removed: int,
    exit_code: int,
    console_lines: Sequence[str],
) -> str:
    overall = (100.0 * total_hits / total_items) if total_items else 0.0
    passed = overall >= PASS_THRESHOLD
    zero_hit_screens = [r for r in results if r.hits == 0]

    lines: List[str] = []
    lines.append("# OmniBox 设计稿中文文案覆盖率报告")
    lines.append("")
    lines.append("> 本报告由 `scripts/check-screens.py` 自动生成，请勿手工修改。")
    lines.append(f"> 生成命令：`python scripts/check-screens.py`（设计稿：`{md_escape(display_path(design_dir))}`）")
    lines.append(f"> 本次退出码：`{exit_code}`。")
    lines.append("")

    lines.append("## 1. 方法学")
    lines.append("")
    lines.append("### 1.1 数据源")
    lines.append("")
    lines.append(
        f"- 设计稿：`{display_path(design_dir)}`。每个含 `code.html` 的直接子目录视为一屏，屏 ID 取目录名。"
    )
    lines.append(
        f"- 共发现 **{code_dir_count}** 个含 `code.html` 的目录；显式排除 **{len(ignored_dirs)}** 个；纳入统计 **{screen_count}** 屏。"
    )
    lines.append(
        f"- Kotlin 源码：`app/`、`core/`、`feature/` 下共 **{kotlin_count}** 个 `.kt` 文件；排除 `reference/`、`build/`、`src/test/`、`src/androidTest/`。"
    )
    lines.append("- 设计稿仅读取 `<body>` 内文本；不执行 JavaScript、不加载 CSS、不做 OCR。")
    lines.append("")

    lines.append("### 1.2 可见中文提取规则")
    lines.append("")
    lines.append("1. 先删除 HTML 注释、`<script>...</script>`、`<style>...</style>`。")
    lines.append("2. 删除 Material Symbols / Material Icons 图标字体元素，避免 `search`、`offline_pin` 等图标连字名混入文案。")
    lines.append("3. 把 `div`、`p`、`li`、`h1`～`h6`、`header`、`button`、`label`、`td`、`br` 等块级标签替换为换行，其余标签替换为空格。这样相邻块不会粘成一条，而行内数字仍会保留在同一句里。")
    lines.append("4. 对剩余文本执行 `html.unescape`，按 `\\s+` 折叠为单个空格并去除首尾空白。")
    lines.append("5. 只保留含 CJK 字符（`[\\u4e00-\\u9fff]`）的文本片段。")
    lines.append("6. 过滤规则（脚本常量与注释中同样写明）：")
    lines.append("   - 长度小于 2 的碎片不计；")
    lines.append("   - 纯数字、纯符号、纯单位（如 `12`、`%`、`12 %`）不计；")
    lines.append("   - 明显占位符（`Lorem`、`TODO`、`TBD`、`placeholder`、`占位符`、`待补充`、`示例文本`）不计；")
    lines.append("   - 同一屏内完全相同的文案只计一次。")
    lines.append(f"- 本次被占位符规则剔除：**{placeholders_removed}** 条。")
    lines.append("")
    lines.append(
        "说明：本脚本只统计 HTML 文本节点，**不统计** `placeholder`、`aria-label`、`title` 等属性中的中文，也不判断 CSS 是否把某个元素隐藏。"
        "因此「可见」是结构意义上的可见，不是渲染后的像素级可见。"
    )
    lines.append("")

    lines.append("### 1.3 匹配规则")
    lines.append("")
    lines.append("对每条设计文案，依次尝试以下三级匹配；任一级命中即计为「已覆盖」：")
    lines.append("")
    lines.append("1. **严格匹配**：设计文案（空白折叠后）是 Kotlin 源码原文（空白折叠后）的连续子串。")
    lines.append("2. **宽松匹配**：两边都去掉数字、标点、空白、格式化占位符（`%d`、`%1$s`、`%.2f`、`${...}`）后，只保留汉字与拉丁字母并转小写，再做子串比较。这是需求指定的宽松口径，用于让 `\"剩余 %d 天\"` 命中设计稿「剩余 12 天」这类片段。")
    lines.append("3. **纯中文兜底**：仅当设计文案的纯中文骨架不少于 4 个汉字，且该骨架出现在源码的纯中文骨架中时命中；用于处理 Kotlin 字符串模板或英文缩写夹在中间的长句。")
    lines.append("")
    lines.append(
        "宽松匹配只证明「字符序列在源码某处出现过」，不证明它属于同一控件、同一屏或同一语境；短词（如「设置」「打开」）尤其容易因全局搜索而命中。"
        "因此该指标衡量的是**文案存在性覆盖**，不是逐屏 UI 等价性证明。"
    )
    lines.append("")

    lines.append("### 1.4 为什么用文案覆盖替代像素比对")
    lines.append("")
    lines.append(
        "本机没有 Android 模拟器与系统镜像，无法逐屏启动应用并截图，因此无法做像素级 diff。"
        "设计包是 Google Stitch 导出的静态 HTML，界面文案以文本节点形式存在；Kotlin/Compose 实现的界面文案也以字符串字面量形式存在。"
        "在该约束下，中文文案覆盖率是一个**可离线、可复现、可审计**的代理指标：它能发现「整屏未实现」「关键标签漏写」「文案改写」等问题，但不能替代真实截图比对。"
    )
    lines.append("")

    lines.append("### 1.5 局限")
    lines.append("")
    lines.append("- 文案存在不等于布局、层级、颜色、间距、交互正确；本报告不评价视觉还原度。")
    lines.append("- 只读取 HTML 文本节点；`placeholder`、`aria-label`、`title` 等属性未计入，可能低估设计稿文案总量。")
    lines.append("- 不执行 CSS；带 `hidden` / 响应式隐藏类的元素仍可能被抽取，可能轻微高估设计稿文案总量。")
    lines.append("- 源码语料为 `.kt` 原文（含注释），注释中的相同文字也会被算作命中，可能轻微高估覆盖率。")
    lines.append("- 长段落按整行比对，任何改写、增删标点之外的字词都可能造成未命中；短词则可能因全局出现而误命中。")
    lines.append("- 覆盖率高低不代表功能完成度或代码质量；设计稿中纯图标、纯数值、动态单位等本就无可匹配中文的屏，覆盖率会天然偏低。")
    lines.append("")

    lines.append("## 2. 汇总")
    lines.append("")
    lines.append("| 指标 | 数值 |")
    lines.append("|---|---:|")
    lines.append(f"| 总屏数 | {screen_count} |")
    lines.append(f"| 设计文案总条数 | {total_items} |")
    lines.append(f"| 严格命中 | {strict_hits} |")
    lines.append(f"| 宽松匹配补充 | {loose_hits} |")
    lines.append(f"| 纯中文兜底补充 | {cjk_hits} |")
    lines.append(f"| 命中数 | {total_hits} |")
    lines.append(f"| 总覆盖率 | {overall:.1f}% |")
    lines.append("")
    if passed:
        lines.append(
            f"**结论：总覆盖率 {overall:.1f}%（{total_hits}/{total_items}），已达到 {PASS_THRESHOLD:.0f}% 门槛。**"
        )
    else:
        lines.append(
            f"**结论：总覆盖率 {overall:.1f}%（{total_hits}/{total_items}），未达到 {PASS_THRESHOLD:.0f}% 门槛。**"
        )
    lines.append("")

    lines.append("## 3. 逐屏明细")
    lines.append("")
    lines.append("| 屏 ID | 设计条目数 | 命中数 | 覆盖率 | 未命中文案（前 5 条） |")
    lines.append("|---|---:|---:|---:|---|")
    for result in results:
        lines.append(
            f"| `{md_escape(result.screen_id)}` | {result.total} | {result.hits} | "
            f"{result.rate:.1f}% | {format_misses(result.misses)} |"
        )
    lines.append("")

    lines.append("## 4. 零命中屏")
    lines.append("")
    if zero_hit_screens:
        lines.append("以下屏的设计文案在 Kotlin 源码中一条都没有找到：")
        lines.append("")
        for result in zero_hit_screens:
            lines.append(f"- `{result.screen_id}`（{result.total} 条设计文案，0 条命中）")
    else:
        lines.append("本次无零命中屏。")
    lines.append("")

    lines.append("## 5. 已知不计入的目录")
    lines.append("")
    for name, reason in KNOWN_EXCLUDED.items():
        if name in ignored_dirs:
            lines.append(f"- `{name}`：{reason}")
        else:
            lines.append(f"- `{name}`：本次未在扫描结果中找到（规则仍保留）。")
    if non_code_dirs:
        lines.append(
            "- 以下目录不含 `code.html`，不构成可提取静态文案的屏，未计入："
            + "、".join(f"`{name}`" for name in non_code_dirs)
            + "。"
        )
    lines.append("")

    lines.append("## 6. 本次运行输出")
    lines.append("")
    lines.append("```text")
    lines.extend(console_lines)
    lines.append("```")
    lines.append("")

    return "\n".join(lines)


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------


def main() -> int:
    parser = argparse.ArgumentParser(
        description="比对 Stitch 设计稿中文文案与 OmniBox Kotlin 源码的覆盖率。",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "示例：\n"
            "  python scripts/check-screens.py\n"
            "  python scripts/check-screens.py --design-dir \"E:/path/to/stitch_/stitch_\"\n"
        ),
    )
    parser.add_argument(
        "--design-dir",
        default=None,
        help="设计稿根目录（含多个带 code.html 的子目录）；默认向上查找 ../stitch_/stitch_。",
    )
    args = parser.parse_args()

    design_dir = resolve_design_dir(args.design_dir)
    if design_dir is None:
        tried = [
            PROJECT_ROOT.parent / "stitch_" / "stitch_",
            Path.cwd().parent / "stitch_" / "stitch_",
            Path.cwd() / "stitch_" / "stitch_",
        ]
        message = ["未找到设计稿目录。已尝试："]
        message.extend("  - " + display_path(path) for path in tried)
        message.append('请用 --design-dir "<设计稿根目录>" 指定，例如：')
        message.append('  python scripts/check-screens.py --design-dir "../stitch_/stitch_"')
        return fail("\n".join(message))

    if not design_dir.is_dir():
        return fail(f"--design-dir 指向的路径不是目录：{display_path(design_dir)}")

    kotlin_files = collect_kotlin_files()
    if not kotlin_files:
        return fail(
            f"在 {display_path(PROJECT_ROOT)} 的 app/、core/、feature/ 下没有找到可读的 .kt 文件。"
        )

    source_text = "\n".join(read_utf8(path) for path in kotlin_files)
    source_flat = WS_RE.sub(" ", source_text)
    source_skeleton = skeleton(source_text)
    source_cjk = cjk_only(source_text)

    results, code_dir_count, ignored_dirs, non_code_dirs = scan(
        design_dir, source_flat, source_skeleton, source_cjk
    )
    if not results:
        return fail(
            f"在 {display_path(design_dir)} 下没有找到可统计的屏（需要子目录内含 code.html，且未被排除）。"
        )

    total_items = sum(result.total for result in results)
    total_hits = sum(result.hits for result in results)
    strict_hits = sum(result.strict_hits for result in results)
    loose_hits = sum(result.loose_hits for result in results)
    cjk_hits = sum(result.cjk_hits for result in results)
    overall = (100.0 * total_hits / total_items) if total_items else 0.0
    exit_code = 0 if overall >= PASS_THRESHOLD else 1

    placeholders_removed = sum(result.placeholders_removed for result in results)

    console_lines = format_console(results, total_hits, total_items)
    report = build_report(
        design_dir=design_dir,
        screen_count=len(results),
        code_dir_count=code_dir_count,
        ignored_dirs=ignored_dirs,
        non_code_dirs=non_code_dirs,
        kotlin_count=len(kotlin_files),
        results=results,
        total_hits=total_hits,
        total_items=total_items,
        strict_hits=strict_hits,
        loose_hits=loose_hits,
        cjk_hits=cjk_hits,
        placeholders_removed=placeholders_removed,
        exit_code=exit_code,
        console_lines=console_lines,
    )

    try:
        with REPORT_PATH.open("w", encoding="utf-8", newline="\n") as handle:
            handle.write(report)
    except OSError as exc:
        return fail(f"报告写入失败：{display_path(REPORT_PATH)}（{exc}）")

    for line in console_lines:
        print(line)

    if exit_code == 0:
        print(
            f"结论：总覆盖率 {overall:.1f}%（{total_hits}/{total_items}），"
            f"已达到 {PASS_THRESHOLD:.0f}% 门槛。"
        )
    else:
        print(
            f"结论：总覆盖率 {overall:.1f}%（{total_hits}/{total_items}），"
            f"未达到 {PASS_THRESHOLD:.0f}% 门槛。"
        )
    print(f"报告已写入：{display_path(REPORT_PATH)}")

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
