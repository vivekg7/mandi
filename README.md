# Mandi

Daily wholesale (APMC mandi) crop prices across India for farmers and traders, from
the government's open data on data.gov.in. Pick a state, district and market, or
search a commodity across every market. Star the ones you follow to see today's
price and the change since the last report on the home screen.

Mandi price apps on the Play Store are full of ads and trackers. This one is free,
open source (MIT), and has **no ads, no tracking, no analytics and no account**.

## Features

- Browse state → district → market and see every commodity that market reported,
  with min, max and modal price.
- Search a commodity across all markets in India, in English or Hindi
  (`onion` or `प्याज`).
- Favourites (a commodity, variety and grade at a market) on the home screen with
  today's modal price and the change against the previous reported day.
- Prices per quintal and per kg.
- A 30-day trend sparkline from the published history.
- The arrival date is always shown. Markets report late and not every day, so a
  price two or more days old is highlighted.
- Works offline: the last downloaded prices stay on the device.
- Hindi names next to common commodities.
- Material You colours, dark theme, Android 12+.

## Permissions

| Permission | Why                                                                                                                |
| ---------- | ------------------------------------------------------------------------------------------------------------------ |
| `INTERNET` | Download the published price files from `raw.githubusercontent.com`. There are no other network calls of any kind. |

## How the data gets to the app

```
data.gov.in API ──(GitHub Actions, API key in a repo secret)──▶ data branch ──(raw.githubusercontent.com)──▶ app
```

The source is the dataset _Current Daily Price of Various Commodities from Various
Markets (Mandi)_ (resource `9ef84268-d588-465a-a308-a864a43d0070`, from Agmarknet).
Its API needs a personal key, and a key built into an open-source APK is a public
key that anyone could copy and get rate-limited or revoked. So the app never
talks to data.gov.in:

- `.github/workflows/update-prices.yml` runs four times a day, at 10:30, 13:30,
  16:30 and 19:30 IST, when markets report.
- `pipeline/fetch_mandi.py` (Python standard library only) pages through the whole
  dataset and normalises names, dates and prices. It merges the result with the
  previous run's files and writes compact JSON: an index, one file per state, and
  a rolling 30-day history per state. The format is in
  [docs/data-format.md](docs/data-format.md).
- The output is force-pushed as a single commit to the `data` branch. History
  lives inside the files, so keeping a commit per run would only make the
  repository grow forever.

**Why raw.githubusercontent.com and not GitHub Pages:** it serves any branch of a
public repository with no Pages setup, it supports gzip, and its five-minute cache
is short compared with the three-hour schedule. Pages would add a deploy step and
a setting to switch on, and gain nothing for a few JSON files.

**Why prices are carried forward for 7 days:** the upstream dataset only holds the
current reports, and markets often report late, skip holidays and close on Sundays.
Without carrying prices forward, half the markets would vanish from the app every
morning. The arrival date is shown on every price so an old price is never
mistaken for today's.

**Why history starts empty:** data.gov.in serves only the current day, so the
pipeline builds history itself, run by run. Trend lines fill in over the first
month after the workflow starts running.

### Setting up the data pipeline (one-time, by hand)

The workflow cannot run until the API key is added:

1. Register at <https://data.gov.in> (top right, **Login / Register**). You can
   sign up with an email address and an OTP.
2. Once signed in, open **My Account** (your name, top right). Your API key is
   shown there; generate one if it is empty. It is a long hexadecimal string.
3. In this repository on GitHub: **Settings → Secrets and variables → Actions →
   New repository secret**. Name it `DATA_GOV_IN_API_KEY` and paste the key as its
   value. Or, from a clone: `gh secret set DATA_GOV_IN_API_KEY --repo vivekg7/mandi`.
4. Run the workflow once by hand: **Actions → Update prices → Run workflow**, or
   `gh workflow run update-prices.yml --repo vivekg7/mandi`. It creates the `data`
   branch. After that, the schedule takes over.
5. Check that <https://raw.githubusercontent.com/vivekg7/mandi/data/index.json>
   lists the states.

Until step 4 has run, the app shows "Could not load prices".

To run the pipeline locally:

```sh
DATA_GOV_IN_API_KEY=… python3 pipeline/fetch_mandi.py --out local/data
python3 -m unittest discover -s pipeline        # normaliser and merge tests
```

The sample key on data.gov.in's API page also works, but it returns only 10
records and is often rate-limited. Format Python with
`ruff format pipeline && ruff check --fix pipeline`.

## Building the app

This machine has no system JDK; point `JAVA_HOME` at Android Studio's bundled JBR:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug testDebugUnitTest
./gradlew assembleRelease   # unsigned unless keystore.properties exists
```

For a signed release, create `keystore.properties` (git-ignored) next to
`settings.gradle.kts` with `storeFile`, `storePassword`, `keyAlias` and
`keyPassword`.

## Design notes

- **Pure Kotlin core.** JSON parsing, rupee formatting with Indian digit grouping,
  per-kg conversion, the change calculation and Hindi name lookup live in
  `core/`, with no Android imports, and are covered by JVM unit tests.
- **Positional JSON rows.** State files are arrays of arrays. Repeating field names
  on every row would make up most of the download.
- **Favourites are per series** (commodity + variety + grade at a market). One
  market often reports several varieties of the same crop at very different
  prices, and averaging them would be misleading.
- **No database.** The app only shows the last published files, so the cache is
  just those files on disk. Favourites are a string set in SharedPreferences.
- **Search is all-India.** It downloads every state's file once (they are small
  and cached until the next pipeline run).
- **Refusing unknown formats.** Every file carries a format version. An older app
  shows its cached data rather than misread a newer layout.

## Licence

MIT, see [LICENSE](LICENSE). Price data: Agmarknet via data.gov.in, under the
[Government Open Data License – India](https://data.gov.in/government-open-data-license-india).
