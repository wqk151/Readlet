#!/usr/bin/env python3
"""用有道词典（dict.youdao.com）补齐 word_levels.tsv 音标缺口（英/美双列）。

目标集合：
- 级别词（16,241）中缺英标或美标的词（补缺路径的词全靠 CSV 音标，无 LLM 兜底）；
- --words / --list 显式指定词（如 flowerbed/nonstop 这类存量卡片的缺口词）；
- 显式词不在表内时按「无级别兜底行」插入新行（word + 空级别 + 音标 + 空释义 + 0），
  供词根页词族词等生僻词补音标；抓不到音标的空行会原样保存（word 仅 tab），
  需在跑完后清理（连续失败会自动中止提示）。

幂等：已有音标的词跳过；断点续跑（每 100 词落盘一次，原子替换）。
freedict 不用：其 phonetics 多数只有音频 URL、无 IPA 文本。

用法:
  python3 tools/fill_phonetics.py
  python3 tools/fill_phonetics.py --words flowerbed,nonstop
  python3 tools/fill_phonetics.py --list /tmp/missing.txt
"""
import argparse
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

TSV = Path(__file__).resolve().parent.parent / "app/src/main/assets/word_levels.tsv"
UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0 Safari/537.36")
# pronounce 块内：标签（英/美）+ phonetic span；页面结构：<span class="pronounce">英 <span class="phonetic">[ipa]</span>
PRON_RE = re.compile(r'class="pronounce"[^>]*>\s*(英|美)\s*<span class="phonetic">([^<]+)</span>')
WORKERS = 2
PACE = 0.6          # 每个 worker 请求间隔（秒）
MAX_CONSEC_FAIL = 20   # 连续失败过多视为被限流，中止

lock = threading.Lock()
stats = {"ok": 0, "fail": 0, "skip": 0}
consec_fail = [0]
abort = [False]


def norm(raw: str) -> str:
    t = raw.strip()
    if t.startswith("[") and t.endswith("]") and len(t) > 2:
        return "/" + t[1:-1].strip() + "/"
    return t


def fetch_pron(word: str) -> tuple[str, str]:
    """返回 (英标, 美标)；页面无该词时返回 ("","")。"""
    url = "https://dict.youdao.com/w/eng/" + urllib.parse.quote(word)
    last_err: Exception | None = None
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=15) as resp:
                html = resp.read().decode("utf-8", "ignore")
            pron = {}
            for m in PRON_RE.finditer(html):
                pron[m.group(1)] = norm(m.group(2))
            return pron.get("英", ""), pron.get("美", "")
        except urllib.error.HTTPError as e:
            last_err = e
            time.sleep(3 + attempt * 3)   # 429/5xx 退避
        except Exception as e:            # 网络/超时
            last_err = e
            time.sleep(2 + attempt * 2)
    print(f"  [fail] {word}: {last_err}")
    return "", ""


def worker(words: list[str], rows: dict[str, list[str]], start: int, step: int):
    for i in range(start, len(words), step):
        if abort[0]:
            return
        word = words[i]
        with lock:
            stats["skip"] += 1
        uk, us = fetch_pron(word)
        with lock:
            if uk or us:
                stats["ok"] += 1
                consec_fail[0] = 0
            else:
                stats["fail"] += 1
                consec_fail[0] += 1
                if consec_fail[0] >= MAX_CONSEC_FAIL:
                    abort[0] = True
                    print("连续失败过多，疑似被限流，中止。")
        if uk or us:
            with lock:
                rows[word][2] = uk if uk else rows[word][2]
                rows[word][3] = us if us else rows[word][3]
        time.sleep(PACE)


def save(rows: dict[str, list[str]], order: list[str]) -> None:
    with lock:
        body = "\n".join("\t".join(rows[w]) for w in order) + "\n"
    tmp = TSV.with_suffix(".tsv.tmp")
    tmp.write_text(body, encoding="utf-8")
    tmp.replace(TSV)
    print(f"[saved] {len(order)} 行")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--words", default="", help="额外指定词（逗号分隔）")
    ap.add_argument("--list", default="", help="从文件读额外指定词（每行一个）")
    args = ap.parse_args()

    lines = TSV.read_text(encoding="utf-8").splitlines()
    rows: dict[str, list[str]] = {}
    order: list[str] = []
    for line in lines:
        if not line.strip():
            continue
        p = line.split("\t")
        rows[p[0]] = p
        order.append(p[0])

    # 目标：级别词缺任一音标 + 显式词
    target = [w for w in order if len(rows[w]) > 1 and rows[w][1] and (not rows[w][2] or not rows[w][3])]
    extra: list[str] = []
    if args.words:
        extra += [w.strip().lower() for w in args.words.split(",") if w.strip()]
    if args.list:
        extra += [w.strip().lower() for w in Path(args.list).read_text(encoding="utf-8").splitlines()
                  if w.strip()]
    for w in dict.fromkeys(extra):
        if w not in rows:
            rows[w] = [w, "", "", "", "", "0"]   # 表外词：插入无级别空行，抓取后回填音标
            order.append(w)
        if w not in target and (not rows[w][2] or not rows[w][3]):
            target.append(w)
    print(f"目标词数: {len(target)}（级别词缺口 + 显式 {len(extra)}）")

    filled_since_save = 0
    threads = [threading.Thread(target=worker, args=(target, rows, t, WORKERS), daemon=True)
               for t in range(WORKERS)]
    for t in threads:
        t.start()
    while any(t.is_alive() for t in threads):
        time.sleep(10)
        with lock:
            s = dict(stats)
            progress = len(target) - s["skip"]
        print(f"进度 {progress}/{len(target)} ok={s['ok']} fail={s['fail']}")
        # 每 100 个新填充落盘（断点续跑安全）
        if stats["ok"] - filled_since_save >= 100:
            save(rows, order)
            filled_since_save = stats["ok"]
    for t in threads:
        t.join()
    save(rows, order)
    with lock:
        print(f"完成: ok={stats['ok']} fail={stats['fail']}")


if __name__ == "__main__":
    main()
