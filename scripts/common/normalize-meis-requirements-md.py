# -*- coding: utf-8 -*-
"""Normalize docs/meis-requirements.md spacing / table continuity / style block."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / "docs" / "meis-requirements.md"


def is_table_row(s: str) -> bool:
    t = s.strip()
    return t.startswith("|") and t.endswith("|") and t.count("|") >= 2


def is_heading(s: str) -> bool:
    return bool(re.match(r"^#{1,6}\s+\S", s))


def is_list_item(s: str) -> bool:
    return bool(re.match(r"^(\s*[-*+]|\s*\d+\.)\s+", s))


def is_blockquote(s: str) -> bool:
    return s.lstrip().startswith(">")


def main() -> None:
    text = DOC.read_text(encoding="utf-8")
    text = re.sub(r"\n*整理一下本文档格式[^\n]*\n*$", "\n", text)

    lines = [ln.rstrip() for ln in text.splitlines()]

    # Pass 1: drop blank runs between table rows / list items / blockquotes
    compact: list[str] = []
    i = 0
    n = len(lines)
    while i < n:
        line = lines[i]
        if line.strip() == "":
            j = i + 1
            while j < n and lines[j].strip() == "":
                j += 1
            prev = compact[-1] if compact else ""
            nxt = lines[j] if j < n else ""
            if is_table_row(prev) and is_table_row(nxt):
                i = j
                continue
            if is_list_item(prev) and is_list_item(nxt):
                i = j
                continue
            if is_blockquote(prev) and is_blockquote(nxt):
                i = j
                continue
            if not compact or compact[-1].strip() != "":
                compact.append("")
            i = j
            continue
        compact.append(line)
        i += 1

    # Pass 2: collapse multi-blanks
    out: list[str] = []
    for line in compact:
        if line.strip() == "":
            if out and out[-1].strip() == "":
                continue
            out.append("")
        else:
            out.append(line)

    # Pass 3: blank before headings
    with_before: list[str] = []
    for line in out:
        if is_heading(line) and with_before and with_before[-1].strip() != "":
            with_before.append("")
        with_before.append(line)

    # Pass 4: blank after headings
    with_after: list[str] = []
    for idx, line in enumerate(with_before):
        with_after.append(line)
        if is_heading(line):
            nxt = with_before[idx + 1] if idx + 1 < len(with_before) else None
            if nxt is not None and nxt.strip() != "":
                with_after.append("")

    # Pass 5: blank before tables
    norm: list[str] = []
    for line in with_after:
        if is_table_row(line):
            prev = norm[-1] if norm else None
            if prev is not None and prev.strip() != "" and not is_table_row(prev):
                norm.append("")
        norm.append(line)

    result: list[str] = []
    for line in norm:
        if line.strip() == "":
            if result and result[-1].strip() == "":
                continue
            result.append("")
        else:
            result.append(line)

    while result and result[0].strip() == "":
        result.pop(0)
    while result and result[-1].strip() == "":
        result.pop()

    body = "\n".join(result) + "\n"

    style_block = """## 文档格式约定（统一风格）

> 自 2026-10-26 起，本文档统一按下列版式维护（历史空行膨胀已一次性清理）。

| 项 | 约定 |
|------|------|
| 空行 | 块与块之间最多 **1** 个空行；禁止连续空行 |
| 标题 | ATX（`#` / `##` / `###`）；标题上下各空一行；勿跳级 |
| 表格 | 表头+分隔行+数据行连续书写，**表行之间不得插入空行** |
| 列表 | 同一列表项之间不插空行；列表与前后段落空一行 |
| 引用块 | 连续 `>` 行；落地说明可用引用块 |
| 定稿条目 | 建议用「标题 + 表格/清单」；变更记入第 6 章版本记录 |
| 待办分流 | 缺陷→第 4 章；待确认→第 5 章；暂缓→第 7 章 BACKLOG |
| 行尾 | 去掉行尾空白；文件以单个换行结束 |

"""

    if "## 文档格式约定" not in body:
        m = re.search(r"^## 如何使用本文档\s*$", body, re.M)
        if m:
            start = m.end()
            m2 = re.search(r"^## ", body[start:], re.M)
            if m2:
                insert_at = start + m2.start()
                body = body[:insert_at] + "\n" + style_block + body[insert_at:]
            else:
                body = style_block + body
        else:
            m1 = re.search(r"^# .+$", body, re.M)
            if m1:
                insert_at = m1.end()
                body = body[:insert_at] + "\n\n" + style_block + body[insert_at:]
            else:
                body = style_block + body

    if "| 2.225 |" not in body:
        body = body.replace(
            "| 2.224 | 2026-10-26 15:10:00 |",
            "| 2.225 | 2026-10-26 15:20:00 | — | 统一文档格式：清除空行膨胀、表行连续、新增「文档格式约定」 |\n\n| 2.224 | 2026-10-26 15:10:00 |",
            1,
        )

    DOC.write_text(body.replace("\r\n", "\n"), encoding="utf-8", newline="\n")
    lines2 = body.splitlines()
    ne = sum(1 for L in lines2 if L.strip())
    print(f"lines={len(lines2)} nonempty={ne} empty={len(lines2) - ne} bytes={DOC.stat().st_size}")


if __name__ == "__main__":
    main()
