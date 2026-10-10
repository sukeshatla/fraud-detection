#!/usr/bin/env python3
"""Prints one line of key stats from a Gatling HTML report (Gatling 3.16 no longer writes stats.json).

    python3 load-tests/gatling-summary.py load-tests/target/gatling/<run>/[index.html]
    python3 load-tests/gatling-summary.py --latest failoversimulation
"""
import glob, os, re, sys

COLUMNS = {2: "total", 3: "ok", 4: "ko", 5: "ko_pct", 6: "rps", 7: "min", 8: "p50", 9: "p75", 10: "p95", 11: "p99", 12: "max", 13: "mean"}


def stats(index_html):
    html = open(index_html).read()
    first_row = {}
    for col, value in re.findall(r'<td class="value \w+ col-(\d+)">([^<]*)</td>', html):
        first_row.setdefault(int(col), value.strip())  # first row = global stats
    return {COLUMNS[c]: v for c, v in first_row.items() if c in COLUMNS}


def main():
    if sys.argv[1] == "--latest":
        runs = sorted(r for r in glob.glob(os.path.join(os.path.dirname(__file__), "target/gatling", sys.argv[2] + "-*"))
                      if os.path.exists(os.path.join(r, "index.html")))  # completed runs only
        path = os.path.join(runs[-1], "index.html")
    else:
        path = sys.argv[1] if sys.argv[1].endswith(".html") else os.path.join(sys.argv[1], "index.html")
    s = stats(path)
    print(f"requests={s['total']} ko={s['ko']} ({s['ko_pct']}%) rps={s['rps']} "
          f"p50={s['p50']}ms p95={s['p95']}ms p99={s['p99']}ms max={s['max']}ms")


if __name__ == "__main__":
    main()
