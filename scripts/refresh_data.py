"""Yearly data refresh orchestrator.

Run once a year (or whenever you want to update the bundled papers.json):

    python scripts/refresh_data.py

What it does:
    1. fetch_openalex.py    — pull top-500 by cited_by_count from OpenAlex
    2. score_and_filter.py  — age-normalize, dedup, take top-365
    3. build_assets.py      — slim to APK shape, write to:
         - app/src/main/assets/papers.json   (bundled fallback inside the APK)
         - assets/papers.json                (mirror for GitHub Pages remote fetch)

Year cutoffs derive from today's date (no code edits needed at year boundary).
Override for testing/backfill via env vars EDU_CURR_YEAR / EDU_YEAR_MAX.
"""
import os
import subprocess
import sys
from datetime import date
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

SCRIPTS = Path(__file__).parent
STAGES = [
    ("fetch_openalex.py",   "Stage 1/3 — fetch top-500 from OpenAlex"),
    ("score_and_filter.py", "Stage 2/3 — score, filter, take top-365"),
    ("build_assets.py",     "Stage 3/3 — build slim APK asset"),
]

curr_year = int(os.environ.get("EDU_CURR_YEAR") or date.today().year)
year_max = int(os.environ.get("EDU_YEAR_MAX") or (curr_year - 5))

print(f"=== refresh start: {date.today().isoformat()} ===")
print(f"CURR_YEAR={curr_year}  YEAR_MAX={year_max}\n")

env = os.environ.copy()
env["EDU_CURR_YEAR"] = str(curr_year)
env["EDU_YEAR_MAX"] = str(year_max)

for script, label in STAGES:
    print(f"\n--- {label} ---")
    rc = subprocess.call([sys.executable, str(SCRIPTS / script)], env=env)
    if rc != 0:
        print(f"\n!! {script} failed with exit code {rc}; aborting.")
        sys.exit(rc)

print("\n=== refresh complete ===")
print("Next steps:")
print("  1. git diff app/src/main/assets/papers.json    # sanity-check the new top picks")
print("  2. ./gradlew assembleDebug                     # rebuild APK")
print("  3. (optional) push assets/papers.json to GitHub Pages for remote update")
