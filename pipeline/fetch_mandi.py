#!/usr/bin/env python3
"""Fetch the data.gov.in mandi price dataset and publish compact JSON for the app.

The app cannot call data.gov.in itself: the API needs a key, and a key shipped
inside an open-source APK is a published key. So this script runs from cron on a
host in India (``publish.sh``; data.gov.in refuses GitHub's runners) with the key
from the environment, and writes plain JSON files that the app downloads from the
repository's ``data`` branch.

Output layout (all inside ``--out``)::

    index.json            states with row counts and their latest arrival date
    states/<slug>.json    latest price per series, with the previous day's modal
    history/<slug>.json   up to HISTORY_DAYS of modal prices per series

A *series* is one (district, market, commodity, variety, grade) combination.

The upstream dataset only holds the current day's reports, and markets report
late or not at all on holidays. So each run merges what it fetches into the
files the previous run published: a series that did not report today keeps its
last price for up to LATEST_MAX_AGE_DAYS (the app shows its arrival date), and
history accumulates day by day.

Standard library only, so the host needs no ``pip install`` step.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import math
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

RESOURCE_ID = "9ef84268-d588-465a-a308-a864a43d0070"
API_URL = f"https://api.data.gov.in/resource/{RESOURCE_ID}"
PAGE_SIZE = 1000
HISTORY_DAYS = 30
LATEST_MAX_AGE_DAYS = 7
FORMAT_VERSION = 1
IST = dt.timezone(dt.timedelta(hours=5, minutes=30))
USER_AGENT = "Mandi/1.0"

# Field names have changed case between dataset revisions ("state" vs "State",
# "Min_x0020_Price"), so records are read through this alias table after their
# keys are lower-cased.
FIELD_ALIASES = {
    "state": ("state",),
    "district": ("district",),
    "market": ("market",),
    "commodity": ("commodity",),
    "variety": ("variety",),
    "grade": ("grade",),
    "date": ("arrival_date",),
    "min": ("min_price", "min_x0020_price"),
    "max": ("max_price", "max_x0020_price"),
    "modal": ("modal_price", "modal_x0020_price"),
}

# A row of a state file: series key, then the latest observation and the
# previous one (None when there is no earlier day in history).
#   [district, market, commodity, variety, grade, date, min, max, modal,
#    prev_date, prev_modal]
SERIES_FIELDS = 5


# --------------------------------------------------------------------- normalise


def clean_text(value: object) -> str:
    """Trim and collapse whitespace; the upstream data is inconsistent about both."""
    if value is None:
        return ""
    return re.sub(r"\s+", " ", str(value)).strip()


def parse_date(value: object) -> str | None:
    """Return an ISO date for ``dd/mm/yyyy`` (upstream) or ``yyyy-mm-dd`` input."""
    text = clean_text(value)
    for fmt in ("%d/%m/%Y", "%Y-%m-%d", "%d-%m-%Y"):
        try:
            return dt.datetime.strptime(text, fmt).date().isoformat()
        except ValueError:
            continue
    return None


def parse_price(value: object) -> int | float | None:
    """Prices arrive as strings or numbers in rupees per quintal.

    Whole values become ints so the JSON stays short. Zero, negative or
    unparseable prices are treated as missing.
    """
    if value is None or isinstance(value, bool):
        return None
    try:
        number = float(str(value).replace(",", "").strip())
    except ValueError:
        return None
    if not math.isfinite(number) or number <= 0:
        return None
    return int(number) if number.is_integer() else round(number, 2)


def slugify(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def normalise_record(record: dict) -> dict | None:
    """Turn one upstream record into a clean row, or None if it is unusable."""
    lowered = {str(k).strip().lower(): v for k, v in record.items()}

    def field(name: str) -> object:
        for alias in FIELD_ALIASES[name]:
            if alias in lowered:
                return lowered[alias]
        return None

    row = {
        name: clean_text(field(name))
        for name in ("state", "district", "market", "commodity", "variety", "grade")
    }
    if not (row["state"] and row["market"] and row["commodity"]):
        return None
    row["date"] = parse_date(field("date"))
    modal = parse_price(field("modal"))
    if row["date"] is None or modal is None:
        return None
    row["modal"] = modal
    row["min"] = parse_price(field("min")) or modal
    row["max"] = parse_price(field("max")) or modal
    return row


def series_key(row: dict) -> tuple:
    return (
        row["district"],
        row["market"],
        row["commodity"],
        row["variety"],
        row["grade"],
    )


# ------------------------------------------------------------------------- fetch


def fetch_page(api_key: str, offset: int, limit: int, attempts: int = 5) -> dict:
    query = urllib.parse.urlencode(
        {"api-key": api_key, "format": "json", "offset": offset, "limit": limit}
    )
    request = urllib.request.Request(
        f"{API_URL}?{query}", headers={"User-Agent": USER_AGENT}
    )
    delay = 5.0
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                return json.load(response)
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
            # 403 means a bad key; retrying will not help.
            if isinstance(error, urllib.error.HTTPError) and error.code == 403:
                raise
            if attempt == attempts:
                raise
            print(
                f"offset {offset}: {error}; retrying in {delay:.0f}s", file=sys.stderr
            )
            time.sleep(delay)
            delay *= 2
    raise AssertionError("unreachable")


def fetch_all(api_key: str, page_size: int = PAGE_SIZE) -> list[dict]:
    """Page through the whole dataset. The sample key stops after 10 records."""
    records: list[dict] = []
    offset = 0
    while True:
        page = fetch_page(api_key, offset, page_size)
        batch = page.get("records") or []
        records.extend(batch)
        total = int(page.get("total") or 0)
        print(f"fetched {len(records)} of {total}", file=sys.stderr)
        offset += len(batch)
        if not batch or len(batch) < page_size or offset >= total:
            return records


# ------------------------------------------------------------------------- merge


def read_json(path: Path) -> dict | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def load_previous(out: Path) -> dict[str, dict]:
    """Rebuild per-state working data from the files the last run published.

    Returns {state name: {"latest": {key: row}, "history": {key: {date: modal}}}}.
    """
    states: dict[str, dict] = {}
    index = read_json(out / "index.json") or {}
    for entry in index.get("states", []):
        name, slug = entry.get("name"), entry.get("slug")
        if not name or not slug:
            continue
        data = states.setdefault(name, {"latest": {}, "history": {}})
        state_file = read_json(out / "states" / f"{slug}.json") or {}
        for r in state_file.get("rows", []):
            key = tuple(r[:SERIES_FIELDS])
            date, low, high, modal = r[SERIES_FIELDS : SERIES_FIELDS + 4]
            data["latest"][key] = dict(
                zip(
                    ("district", "market", "commodity", "variety", "grade"),
                    key,
                    strict=True,
                ),
                state=name,
                date=date,
                min=low,
                max=high,
                modal=modal,
            )
        history_file = read_json(out / "history" / f"{slug}.json") or {}
        days = history_file.get("days", [])
        for s in history_file.get("series", []):
            values = s[SERIES_FIELDS]
            points = {d: v for d, v in zip(days, values, strict=False) if v is not None}
            if points:
                data["history"][tuple(s[:SERIES_FIELDS])] = points
    return states


def merge(
    previous: dict[str, dict], rows: list[dict], today: dt.date
) -> dict[str, dict]:
    """Fold freshly fetched rows into the previous state and prune old data."""
    states = {
        name: {"latest": dict(d["latest"]), "history": dict(d["history"])}
        for name, d in previous.items()
    }
    # A date in the future is a data-entry error upstream; tomorrow is allowed
    # because the runner's clock and IST can straddle midnight.
    newest_allowed = (today + dt.timedelta(days=1)).isoformat()
    for row in rows:
        if row["date"] > newest_allowed:
            continue
        data = states.setdefault(row["state"], {"latest": {}, "history": {}})
        key = series_key(row)
        current = data["latest"].get(key)
        if current is None or row["date"] >= current["date"]:
            data["latest"][key] = row
        points = dict(data["history"].get(key, {}))
        points[row["date"]] = row["modal"]
        data["history"][key] = points

    all_dates = [r["date"] for d in states.values() for r in d["latest"].values()]
    if not all_dates:
        return {}
    newest = dt.date.fromisoformat(max(all_dates))
    latest_cutoff = (newest - dt.timedelta(days=LATEST_MAX_AGE_DAYS - 1)).isoformat()
    history_cutoff = (newest - dt.timedelta(days=HISTORY_DAYS - 1)).isoformat()

    result = {}
    for name, data in states.items():
        latest = {k: r for k, r in data["latest"].items() if r["date"] >= latest_cutoff}
        history = {}
        for key, points in data["history"].items():
            kept = {d: v for d, v in points.items() if d >= history_cutoff}
            if kept:
                history[key] = kept
        if latest:
            result[name] = {"latest": latest, "history": history}
    return result


def previous_point(points: dict[str, float], before: str) -> tuple[str, float] | None:
    earlier = [d for d in points if d < before]
    if not earlier:
        return None
    day = max(earlier)
    return day, points[day]


# ------------------------------------------------------------------------- write


def build_outputs(states: dict[str, dict], generated: str) -> dict[str, dict]:
    """Return {relative path: JSON document} for everything to publish."""
    files: dict[str, dict] = {}
    index_states = []
    for name in sorted(states):
        data = states[name]
        slug = slugify(name)
        rows = []
        for key in sorted(data["latest"]):
            r = data["latest"][key]
            prev = previous_point(data["history"].get(key, {}), r["date"])
            rows.append(
                [*key, r["date"], r["min"], r["max"], r["modal"]]
                + ([prev[0], prev[1]] if prev else [None, None])
            )
        files[f"states/{slug}.json"] = {
            "v": FORMAT_VERSION,
            "state": name,
            "generated": generated,
            "rows": rows,
        }

        days = sorted({d for points in data["history"].values() for d in points})
        series = [
            [*key, [data["history"][key].get(d) for d in days]]
            for key in sorted(data["history"])
        ]
        files[f"history/{slug}.json"] = {
            "v": FORMAT_VERSION,
            "state": name,
            "generated": generated,
            "days": days,
            "series": series,
        }

        index_states.append(
            {
                "slug": slug,
                "name": name,
                "markets": len({(k[0], k[1]) for k in data["latest"]}),
                "rows": len(rows),
                "latest": max(r["date"] for r in data["latest"].values()),
            }
        )
    files["index.json"] = {
        "v": FORMAT_VERSION,
        "generated": generated,
        "source": "data.gov.in resource " + RESOURCE_ID,
        "states": index_states,
    }
    return files


def write_outputs(out: Path, files: dict[str, dict]) -> None:
    # Remove files of states that dropped out, so the branch mirrors the index.
    for folder in ("states", "history"):
        for old in (out / folder).glob("*.json"):
            if f"{folder}/{old.name}" not in files:
                old.unlink()
    for relative, document in files.items():
        path = out / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        # Compact separators: these files are downloaded on mobile data.
        path.write_text(
            json.dumps(document, ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8",
        )


def run(out: Path, records: list[dict], now: dt.datetime) -> dict[str, dict]:
    rows = [r for r in (normalise_record(rec) for rec in records) if r]
    print(f"{len(rows)} usable rows of {len(records)} records", file=sys.stderr)
    states = merge(load_previous(out), rows, now.astimezone(IST).date())
    files = build_outputs(states, now.astimezone(dt.UTC).isoformat(timespec="seconds"))
    write_outputs(out, files)
    return files


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, required=True, help="output directory")
    parser.add_argument(
        "--input", type=Path, help="read records from a saved API response instead"
    )
    args = parser.parse_args()

    if args.input:
        records = (read_json(args.input) or {}).get("records", [])
    else:
        api_key = os.environ.get("DATA_GOV_IN_API_KEY", "").strip()
        if not api_key:
            print("DATA_GOV_IN_API_KEY is not set", file=sys.stderr)
            return 2
        records = fetch_all(api_key)
    if not records:
        # Publishing nothing would wipe the app's data; keep the last good run.
        print("no records fetched; leaving the previous output alone", file=sys.stderr)
        return 1
    args.out.mkdir(parents=True, exist_ok=True)
    files = run(args.out, records, dt.datetime.now(dt.UTC))
    print(f"wrote {len(files)} files to {args.out}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
