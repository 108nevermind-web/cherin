"""Phase 0/1: Fetch top-cited papers from Journal of Educational Psychology.

Uses OpenAlex cursor pagination. Single file, minimal deps (Karpathy style).
"""
import io
import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

ISSN_PRINT = "0022-0663"
ISSN_ELECTRONIC = "1939-2176"
TARGET_N = 500
PER_PAGE = 200  # OpenAlex max
MAILTO = "108nevermind@gmail.com"  # polite pool — header only, never persisted
OUT_PATH = Path(__file__).parent.parent / "data" / "raw_top500.json"


def reconstruct_abstract(inv_index):
    if not inv_index:
        return None
    pos = []
    for w, idxs in inv_index.items():
        for i in idxs:
            pos.append((i, w))
    pos.sort()
    return " ".join(w for _, w in pos)


def fetch_page(cursor):
    params = {
        "filter": f"primary_location.source.issn:{ISSN_PRINT}|{ISSN_ELECTRONIC}",
        "sort": "cited_by_count:desc",
        "per-page": str(PER_PAGE),
        "cursor": cursor,
        "mailto": MAILTO,
    }
    url = "https://api.openalex.org/works?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": "edu-psych-app/0.1"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main():
    all_results = []
    cursor = "*"
    page = 0
    meta = None

    while len(all_results) < TARGET_N:
        page += 1
        print(f"page {page}  cursor={cursor[:20]}...  have={len(all_results)}")
        data = fetch_page(cursor)
        if meta is None:
            meta = data.get("meta", {})
            print(f"  total in journal: {meta.get('count')}")
        results = data.get("results", [])
        if not results:
            break
        all_results.extend(results)
        cursor = data.get("meta", {}).get("next_cursor")
        if not cursor:
            break
        time.sleep(0.1)  # be polite

    all_results = all_results[:TARGET_N]
    for r in all_results:
        r["_abstract_reconstructed"] = reconstruct_abstract(r.get("abstract_inverted_index"))

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(
        json.dumps({"meta": meta, "results": all_results}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"\nsaved {len(all_results)} -> {OUT_PATH}")
    print(f"abstracts present: {sum(1 for r in all_results if r['_abstract_reconstructed'])}/{len(all_results)}")


if __name__ == "__main__":
    main()
