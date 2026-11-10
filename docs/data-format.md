# Published data format

The `Update prices` workflow writes these files to the `data` branch. The app
reads them from
`https://raw.githubusercontent.com/vivekg7/mandi/data/<path>`. The producer is
`pipeline/fetch_mandi.py`; the consumer is
`app/src/main/java/com/crylo/mandi/core/MandiJson.kt`. Change both together and
bump `v` if a row layout changes: the app refuses a version it does not know
rather than misreading prices.

All prices are rupees per quintal (100 kg), as reported upstream. Dates are ISO
(`yyyy-mm-dd`) arrival dates, i.e. the day the market reported. `generated` is
the UTC time of the run and is the same in every file of one run. The app uses
it to decide whether a cached state file is still current.

Rows are positional arrays, not objects. Field names repeated on every row
would make up most of the file.

A **series** is one `(district, market, commodity, variety, grade)` within a
state.

## `index.json`

```json
{
  "v": 1,
  "generated": "2026-11-10T08:00:05+00:00",
  "source": "data.gov.in resource 9ef84268-d588-465a-a308-a864a43d0070",
  "states": [
    {
      "slug": "uttar-pradesh",
      "name": "Uttar Pradesh",
      "markets": 251,
      "rows": 3120,
      "latest": "2026-11-10"
    }
  ]
}
```

`slug` is the state name lower-cased, with every run of non-alphanumerics
replaced by `-`. It names the two files below.

## `states/<slug>.json`

The latest observation of every series reported in the last 7 days (counted
back from the newest arrival date in the whole dataset), with the previous
reported day's modal price from history.

```json
{
  "v": 1,
  "state": "Uttar Pradesh",
  "generated": "…",
  "rows": [
    [
      "Agra",
      "Achnera",
      "Wheat",
      "Dara",
      "FAQ",
      "2026-11-10",
      2400,
      2500,
      2450,
      "2026-11-09",
      2400
    ]
  ]
}
```

Row: `district, market, commodity, variety, grade, arrival_date, min, max,
modal, previous_date, previous_modal`. The last two are `null` when history has
no earlier day.

Why 7 days: the upstream dataset only holds the current reports, and many
markets report late, skip holidays or close on Sundays. Without carrying the
last price forward, a market would vanish from the app each morning until it
reported again. The arrival date is always shown, so an old price is never
mistaken for today's.

## `history/<slug>.json`

Up to 30 days of modal prices per series, for the trend lines.

```json
{
  "v": 1,
  "state": "Goa",
  "generated": "…",
  "days": ["2026-11-08", "2026-11-09", "2026-11-10"],
  "series": [["North Goa", "Mapusa", "Onion", "Red", "FAQ", [2000, null, 2100]]]
}
```

Each series' value list is aligned with `days`. `null` means no report that
day. The history is built up by the pipeline itself, run by run. data.gov.in
does not provide it, so it starts empty and fills over the first month.
