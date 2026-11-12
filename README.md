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
  (`onion` or `प्याज`), narrowed to one state with a tap. Search starts in the
  state most of your starred prices are in.
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
data.gov.in API ──(cron on a host in India, pipeline/publish.sh)──▶ data branch ──(raw.githubusercontent.com)──▶ app
```

The source is the dataset _Current Daily Price of Various Commodities from Various
Markets (Mandi)_ (resource `9ef84268-d588-465a-a308-a864a43d0070`, from Agmarknet).
Its API needs a personal key, and a key built into an open-source APK is a public
key that anyone could copy and get rate-limited or revoked. So the app never
talks to data.gov.in:

- `pipeline/publish.sh` runs from cron four times a day, at 10:30, 13:30, 16:30
  and 19:30 IST, when markets report. It runs the pipeline's tests, fetches the
  previous output from the `data` branch, runs the fetch and pushes the result.
- `pipeline/fetch_mandi.py` (Python standard library only) pages through the whole
  dataset and normalises names, dates and prices. It merges the result with the
  previous run's files and writes compact JSON: an index, one file per state, and
  a rolling 30-day history per state. The format is in
  [docs/data-format.md](docs/data-format.md).
- The output is force-pushed as a single commit to the `data` branch. History
  lives inside the files, so keeping a commit per run would only make the
  repository grow forever.

**Why not GitHub Actions:** api.data.gov.in refuses connections from GitHub's
runners, which run outside India (connection refused, then timeouts, on every
attempt), while the same request from an Indian connection answers at once. So
the fetch runs from cron on an always-on Linux machine in India, and Actions only
runs the pipeline's unit tests (`.github/workflows/test-pipeline.yml`). Any
Linux host in India with Python 3.11+ and git works the same way.

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
month after the pipeline starts running.

### Setting up the data pipeline (one-time, by hand)

1. Register at <https://data.gov.in> (top right, **Login / Register**). You can
   sign up with an email address and an OTP.
2. Once signed in, open **My Account** (your name, top right). Your API key is
   shown there; generate one if it is empty. It is a long hexadecimal string.
3. On the host (Linux, in India), create a deploy key and give it write access to
   this repository only:
   ```sh
   ssh-keygen -t ed25519 -N "" -C "mandi data push" -f ~/.ssh/mandi_deploy
   gh repo deploy-key add ~/.ssh/mandi_deploy.pub --repo vivekg7/mandi \
     --allow-write --title "mandi data push"   # from any machine with gh
   ```
4. Clone with that key and store the API key where only you can read it:
   ```sh
   export GIT_SSH_COMMAND="ssh -i $HOME/.ssh/mandi_deploy -o IdentitiesOnly=yes"
   git clone git@github.com:vivekg7/mandi.git ~/Developer/mandi
   mkdir -p ~/.config/mandi && chmod 700 ~/.config/mandi
   read -rs -p "API key: " k && printf 'DATA_GOV_IN_API_KEY=%s\n' "$k" > ~/.config/mandi/env
   chmod 600 ~/.config/mandi/env
   ```
5. Add a cron entry (`crontab -e`; the times assume the host's clock is IST). It
   pulls `main` first, so pipeline changes reach the host without logging in:
   ```cron
   30 10,13,16,19 * * * cd $HOME/Developer/mandi && set -a && . $HOME/.config/mandi/env && set +a && export GIT_SSH_COMMAND="ssh -i $HOME/.ssh/mandi_deploy -o IdentitiesOnly=yes" && git pull -q --ff-only && pipeline/publish.sh >> $HOME/mandi-data.log 2>&1
   ```
6. Run the same command once by hand to create the `data` branch, then check that
   <https://raw.githubusercontent.com/vivekg7/mandi/data/index.json> lists the
   states.

Until step 6 has run, the app shows "Could not load prices". If the host is down,
the app keeps showing the last published prices with their arrival dates.

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

## Releasing on Google Play

```sh
./gradlew lintRelease testDebugUnitTest bundleRelease
# app/build/outputs/bundle/release/app-release.aab
```

- **Signing.** The app uses Play App Signing: Google holds the key that signs what
  users install, and `keystore.properties` points at the _upload_ key. A lost
  upload key can be reset from the Play Console. A lost app signing key could not
  be, which is why Google keeps it. The R8 mapping file is inside the bundle, so
  Play deobfuscates crash reports without a separate upload.
- **Store listing.** Text, icon, feature graphic and screenshots are in
  [`fastlane/metadata/android/en-US/`](fastlane/metadata/android/en-US). F-Droid
  reads the same layout. Bump `versionCode` and add
  `changelogs/<versionCode>.txt` for every release.
- **Screenshots** must be no taller than 2:1. Most current phones are taller, so
  take them with the emulator's display set to `adb shell wm size 1080x2160`, and
  run `adb shell wm size reset` afterwards.
- **Not a government app.** Play requires an app that shows government data to say,
  in the app and in the listing, that it is not affiliated with the government, and
  to link the source. The note under every price list does this. Keep it if that
  screen is reworked.

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
- **Search filters by state before it limits.** Results come 300 at a time, with
  "Showing 300 of N" and a button for more. Tamil Nadu reports more than half the
  rows for common vegetables, so a plain limit over the all-India list used to
  drop whole states without saying so, and left everyone else scrolling past
  Tamil Nadu to reach their own markets. No district filter: within one state a
  commodity rarely has more than a few dozen rows, and each row names its
  district.
- **Refusing unknown formats.** Every file carries a format version. An older app
  shows its cached data rather than misread a newer layout.

## Licence

MIT, see [LICENSE](LICENSE). Price data: Agmarknet via data.gov.in, under the
[Government Open Data License – India](https://data.gov.in/government-open-data-license-india).
