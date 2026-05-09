"""Phase 1 (D-plan): build the final APK asset file.

No LLM. No Korean translation at build time. ML Kit handles it on-device.
Just slim the candidates down to the fields the app needs.

Writes directly to app/src/main/assets/papers.json so the next Gradle build
picks it up (also writes to ./assets/ for the optional GitHub Pages mirror).
"""
import html
import io
import json
import re
import sys
from datetime import date
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).parent.parent
IN = ROOT / "data" / "candidates_365.json"
OUT_BUNDLED = ROOT / "app" / "src" / "main" / "assets" / "papers.json"
OUT_MIRROR = ROOT / "assets" / "papers.json"  # legacy mirror dir
OUT_WEB = ROOT / "docs" / "papers.json"       # PWA (iPhone-friendly) — GitHub Pages /docs


def clean_text(s):
    """Decode HTML entities (some OpenAlex abstracts are double-encoded:
    `&` -> `&amp;` -> `&amp;amp;`). Two passes covers both. Also collapse
    weird whitespace runs."""
    if not s:
        return s
    out = html.unescape(s)
    if "&" in out:
        out = html.unescape(out)
    return re.sub(r"\s+", " ", out).strip()


papers = json.loads(IN.read_text(encoding="utf-8"))
print(f"input: {len(papers)} papers")

slim = []
for i, p in enumerate(papers):
    # Strip OpenAlex URL prefix from id (W123456 instead of full URL)
    pid = (p.get("id") or "").rsplit("/", 1)[-1]
    doi_url = p.get("doi") or ""
    doi = doi_url.replace("https://doi.org/", "") if doi_url else None

    slim.append({
        "dayIndex": i,                      # 0-based position in score-sorted array
        "id": pid,
        "doi": doi,
        "title": clean_text(p.get("title_en")),
        "abstract": clean_text(p.get("abstract_en")),
        "year": p.get("year"),
        "citedBy": p.get("cited_by"),
        "scorePerYear": p.get("score"),
        "authors": p.get("authors") or [],
        "oaPdfUrl": p.get("oa_pdf_url"),
    })

today = date.today()
curr_year = today.year
year_max = curr_year - 5
asset = {
    "version": today.isoformat(),
    "source": "OpenAlex (Journal of Educational Psychology, ISSN 0022-0663 + 1939-2176)",
    "ranking": f"cited_by_count / max({curr_year} - year, 3), year <= {year_max}",
    "count": len(slim),
    "papers": slim,
}

payload = json.dumps(asset, ensure_ascii=False, indent=2)
for out_path in (OUT_BUNDLED, OUT_MIRROR, OUT_WEB):
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(payload, encoding="utf-8")
    size_kb = out_path.stat().st_size // 1024
    print(f"saved -> {out_path}  ({size_kb} KB)")
print(f"dayIndex 0 = {slim[0]['title'][:60]}")
print(f"dayIndex 364 = {slim[-1]['title'][:60]}")
