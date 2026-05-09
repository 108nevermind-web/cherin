"""Look at the data. What's actually in top-50?"""
import io
import json
import sys
from collections import Counter
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

DATA = Path(__file__).parent.parent / "data" / "raw_top50.json"
results = json.loads(DATA.read_text(encoding="utf-8"))["results"]

print(f"=== n = {len(results)} ===\n")

# 1. Year distribution by decade
decades = Counter((r.get("publication_year") or 0) // 10 * 10 for r in results)
print("Year decade distribution:")
for d in sorted(decades):
    print(f"  {d}s: {'#' * decades[d]} ({decades[d]})")

# 2. Type distribution
types = Counter(r.get("type") for r in results)
print("\nType distribution:")
for t, c in types.most_common():
    print(f"  {t}: {c}")

# 3. Abstract presence
with_abs = sum(1 for r in results if r.get("_abstract_reconstructed"))
print(f"\nAbstract present: {with_abs}/{len(results)}")

# 4. Missing-abstract papers — what are they?
print("\nPapers WITHOUT abstract:")
for r in results:
    if not r.get("_abstract_reconstructed"):
        print(f"  [{r.get('cited_by_count'):>5}] ({r.get('publication_year')}) {(r.get('title') or '')[:80]}")

# 5. Citations-per-year (recency-adjusted) — what would top 10 look like?
print("\n=== Top 10 by citations / age (recency-adjusted) ===")
CURR = 2026
scored = []
for r in results:
    y = r.get("publication_year")
    c = r.get("cited_by_count", 0)
    if not y or y >= CURR:
        continue
    age = max(CURR - y, 1)
    scored.append((c / age, c, y, r.get("title") or "", r.get("type"), bool(r.get("_abstract_reconstructed"))))
scored.sort(reverse=True)
for rate, c, y, t, ty, has_abs in scored[:10]:
    flag = "" if has_abs else "  [NO ABSTRACT]"
    print(f"  {rate:6.1f}/yr  cites={c:<5} ({y}) [{ty}] {t[:70]}{flag}")

# 6. Field-of-study / concepts of top 5
print("\n=== Concepts (top 5 by raw cites) ===")
for r in results[:5]:
    concepts = [(c.get("display_name"), round(c.get("score") or 0, 2)) for c in (r.get("concepts") or [])[:5]]
    print(f"  {(r.get('title') or '')[:60]}")
    for n, s in concepts:
        print(f"      - {n} ({s})")

# 7. Open access status (does the user actually be able to read more later?)
oa_status = Counter((r.get("open_access") or {}).get("oa_status") for r in results)
print(f"\nOA status: {dict(oa_status)}")
