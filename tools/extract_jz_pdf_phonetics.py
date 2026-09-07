#!/usr/bin/env python3
"""从《英语词汇的奥秘（音标精排版）》(PDF) 提取词 → 音标，补 word_levels.tsv 英标缺口。

背景：epub 版（同一 Word 源）转换丢字严重；PDF 文本层完整（pdftotext -layout 每行
`词    [音标]    [拆解] 中文义`）。书音标为老式转写（: 表长音、' 主重音、, 次重音），
仅作词表（柯林斯/有道）之后的兜底层；有道/柯林斯都缺的词才落这里。

规则：
- 只处理 wordroot 词族词（词源页展示面），避免无关词膨胀词表；
- 词已在 tsv：仅填英标列（列 3）空缺；
- 词不在 tsv：追加无级别行（word + 英标 + 空美标/释义）；
- 幂等：已有音标的词跳过；重复运行不重复追加。

用法:
  python3 tools/extract_jz_pdf_phonetics.py --pdf <路径> [--dry-run]
"""
import argparse
import re
import subprocess
from pathlib import Path

TSV = Path(__file__).resolve().parent.parent / "app/src/main/assets/word_levels.tsv"
ROOTS = Path(__file__).resolve().parent.parent / "app/src/main/assets/wordroot.txt"

WORD = re.compile(r"^([A-Za-z][A-Za-z'\-]*)\s+(.+)")


def looks_ipa(s: str) -> bool:
    if not s or len(s) < 3:
        return False
    if re.search(r"[\u4e00-\u9fff]", s) or re.search(r"[\[\]＝=→]", s):
        return False
    # 音标字符集合（老式转写含 : ' , 与 IPA 元音/辅音）
    if not re.search(r"[əæɔɪʊʌɑɒθðʃʒŋeεё]", s, re.I):
        return False
    return True


def extract(pdf: str) -> dict[str, str]:
    out = subprocess.run(["pdftotext", "-layout", pdf, "-"],
                         capture_output=True, text=True).stdout
    pairs: dict[str, str] = {}
    for ln in out.splitlines():
        m = WORD.match(ln)
        if not m:
            continue
        w = m.group(1).lower()
        if w in pairs or len(w) < 2:
            continue
        b = re.match(r"^\s*(\[[^\]]{1,45}\])\s*(.*)$", m.group(2))
        if not b:
            continue
        cand = b.group(1).strip("[]").strip()
        if looks_ipa(cand):
            pairs[w] = cand
    return pairs


def family_words() -> set[str]:
    import json
    roots = json.loads(ROOTS.read_text(encoding="utf-8"))
    out: set[str] = set()
    for e in roots.values():
        for w in e.get("example", []):
            out.add(w.strip().lower())
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pdf", required=True, help="《英语词汇的奥秘(音标精排版)》PDF 路径")
    ap.add_argument("--dry-run", action="store_true", help="只统计不写文件")
    args = ap.parse_args()

    pairs = extract(args.pdf)
    fam = family_words()
    print(f"PDF 提取 词→音标: {len(pairs)}")

    lines = TSV.read_text(encoding="utf-8").splitlines()
    rows: dict[str, list[str]] = {}
    order: list[str] = []
    for line in lines:
        if not line.strip():
            continue
        p = line.split("\t")
        rows[p[0]] = p
        order.append(p[0])

    fill = app = skip_have = skip_other = 0
    samples = []
    for w, ipa in sorted(pairs.items()):
        if w not in fam:
            skip_other += 1
            continue
        if w in rows:
            p = rows[w]
            if p[2].strip() or p[3].strip():
                skip_have += 1
                continue
            p[2] = ipa
            fill += 1
            samples.append(w)
        else:
            rows[w] = [w, "", ipa, "", "", "0"]
            order.append(w)
            app += 1
            samples.append(w)

    print(f"词族内命中: 已填 {fill}（tsv 已有行） + 追加 {app}（tsv 无）; 已有音标跳过 {skip_have}; 非词族跳过 {skip_other}")
    print("样例:", samples[:20])

    if not args.dry_run and (fill or app):
        body = "\n".join("\t".join(rows[w]) for w in order) + "\n"
        tmp = TSV.with_suffix(".tsv.tmp")
        tmp.write_text(body, encoding="utf-8")
        tmp.replace(TSV)
        print(f"[saved] {len(order)} 行")


if __name__ == "__main__":
    main()
