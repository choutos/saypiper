#!/usr/bin/env python3
"""Writes app/released-ids.json from the shipped packs, for the commit that is tagged as a release (ADR section 4,
"Release bookkeeping"). Run it on the release commit only; ReleasedIdsTest then guards the ids from that release on.

  scripts/release/released-ids.py --version-name 1.0.0 --version-code 1

Existing retired ids are kept. A unit or word already recorded keeps its place: the test, not this script,
decides whether a change to released ids is allowed.
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTENT = ROOT / "app/src/main/assets/content"
RECORD = ROOT / "app/released-ids.json"

parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument("--version-name", required=True)
parser.add_argument("--version-code", required=True, type=int)
args = parser.parse_args()

previous = json.loads(RECORD.read_text(encoding="utf-8")) if RECORD.exists() else {}
units = dict(previous.get("units", {}))
for grade in json.loads((CONTENT / "grades.json").read_text(encoding="utf-8"))["grades"]:
    pack = json.loads((CONTENT / grade["pack"]).read_text(encoding="utf-8"))
    for unit in pack["units"]:
        released = units.get(unit["id"], [])
        units[unit["id"]] = released + [w["id"] for w in unit["words"] if w["id"] not in released]

record = {
    "comment": "Ids shipped in a release. Never remove or reuse one; list it under retired instead (ADR section 4).",
    "releases": previous.get("releases", []) + [{"versionName": args.version_name, "versionCode": args.version_code}],
    "units": units,
    "retired": previous.get("retired", []),
}
RECORD.write_text(json.dumps(record, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(f"{RECORD.relative_to(ROOT)}: {len(units)} units, {sum(map(len, units.values()))} words")
