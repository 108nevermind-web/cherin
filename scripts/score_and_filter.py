"""Phase 1: Score, filter, dedup. Produce top-365 candidate pool.

Year cutoffs auto-derive from the current calendar year so the yearly refresh
job (scripts/refresh_data.py) doesn't need code edits. Override with env vars
EDU_CURR_YEAR / EDU_YEAR_MAX for testing or backfills.
"""
import io
import json
import os
import sys
from collections import Counter
from datetime import date
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

CURR_YEAR = int(os.environ.get("EDU_CURR_YEAR") or date.today().year)
YEAR_MAX = int(os.environ.get("EDU_YEAR_MAX") or (CURR_YEAR - 5))  # ≥5y citation window
TARGET = 365
print(f"config: CURR_YEAR={CURR_YEAR}  YEAR_MAX={YEAR_MAX}  TARGET={TARGET}")
RAW = Path(__file__).parent.parent / "data" / "raw_top500.json"
OUT = Path(__file__).parent.parent / "data" / "candidates_365.json"

raw = json.loads(RAW.read_text(encoding="utf-8"))["results"]
print(f"raw: {len(raw)}")

# Step 1: must have abstract
step1 = [r for r in raw if r.get("_abstract_reconstructed")]
print(f"after abstract filter: {len(step1)}  (-{len(raw)-len(step1)})")

# Step 2: year <= 2021
step2 = [r for r in step1 if (r.get("publication_year") or 0) <= YEAR_MAX]
print(f"after year<={YEAR_MAX}:    {len(step2)}  (-{len(step1)-len(step2)})")

# Step 3: dedup by DOI (lowercase, strip)
seen_doi = set()
step3 = []
dups = 0
for r in step2:
    doi = (r.get("doi") or "").lower().strip()
    if doi and doi in seen_doi:
        dups += 1
        continue
    if doi:
        seen_doi.add(doi)
    step3.append(r)
print(f"after DOI dedup:        {len(step3)}  (-{dups})")

# Step 4: must be type=article (skip editorials/reviews if any sneak in)
step4 = [r for r in step3 if r.get("type") == "article"]
print(f"after type=article:     {len(step4)}  (-{len(step3)-len(step4)})")

# Step 5: age-normalized score
for r in step4:
    y = r["publication_year"]
    age = max(CURR_YEAR - y, 3)
    r["_score"] = r["cited_by_count"] / age

step4.sort(key=lambda r: r["_score"], reverse=True)
top = step4[:TARGET]
print(f"\ntop {TARGET} selected (need at least {TARGET}; have {len(step4)} after filters)")

# Distributions
print("\n=== year distribution (decade) ===")
decades = Counter((r["publication_year"]) // 10 * 10 for r in top)
for d in sorted(decades):
    print(f"  {d}s: {'#' * decades[d]} ({decades[d]})")

print("\n=== citation rate distribution ===")
scores = sorted([r["_score"] for r in top])
print(f"  min:    {scores[0]:.1f}/yr")
print(f"  median: {scores[len(scores)//2]:.1f}/yr")
print(f"  max:    {scores[-1]:.1f}/yr")
print(f"  mean:   {sum(scores)/len(scores):.1f}/yr")

print("\n=== top 15 by age-normalized score ===")
for r in top[:15]:
    title = (r.get("title") or "")[:75]
    print(f"  {r['_score']:6.1f}/yr  cites={r['cited_by_count']:<5} ({r['publication_year']}) {title}")

print("\n=== bottom 5 of pool (the cutoff line) ===")
for r in top[-5:]:
    title = (r.get("title") or "")[:75]
    print(f"  {r['_score']:6.1f}/yr  cites={r['cited_by_count']:<5} ({r['publication_year']}) {title}")

# Concept distribution (rough relevance signal)
print("\n=== top concepts across pool (proxy for content mix) ===")
all_concepts = Counter()
for r in top:
    for c in (r.get("concepts") or [])[:3]:  # top-3 concepts per paper
        if (c.get("score") or 0) >= 0.3:
            all_concepts[c.get("display_name")] += 1
for name, cnt in all_concepts.most_common(20):
    print(f"  {cnt:3d}  {name}")

# Save slim version (only fields we need downstream)
slim = []
for r in top:
    primary = (r.get("primary_location") or {})
    src = primary.get("source") or {}
    oa = r.get("open_access") or {}
    best_oa = r.get("best_oa_location") or {}
    slim.append({
        "id": r.get("id"),
        "doi": r.get("doi"),
        "title_en": r.get("title"),
        "abstract_en": r.get("_abstract_reconstructed"),
        "year": r.get("publication_year"),
        "cited_by": r.get("cited_by_count"),
        "score": round(r["_score"], 2),
        "authors": [a.get("author", {}).get("display_name") for a in (r.get("authorships") or [])[:5]],
        "venue": src.get("display_name"),
        "concepts": [
            {"name": c.get("display_name"), "score": round(c.get("score") or 0, 2)}
            for c in (r.get("concepts") or [])[:5]
        ],
        "oa_status": oa.get("oa_status"),
        "oa_pdf_url": best_oa.get("pdf_url"),
    })

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps(slim, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"\nsaved -> {OUT}  ({OUT.stat().st_size // 1024} KB)")
