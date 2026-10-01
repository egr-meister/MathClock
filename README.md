# MathClock

MathClock is an offline calculator and time-learning app for children, written natively in Kotlin with Jetpack Compose. It has no accounts, no network access, no ads and no analytics.

## Features

- **Main board ("Clock and Math Board")**: a large interactive analog clock with a synchronized digital display and a 12/24-hour control, next to a notebook-style **Time Practice Board** (Read the Clock, Set the Clock, Time After, Minutes Between, Today's Practice). There is also a **Calculator strip** and a **Progress bookmark** that shows how many questions were answered today. Phones in portrait show the clock above the practice board; windows 700 dp and wider show them side by side.
- **Explore the Clock**: drag either hand, or use the accessible Hour, Minute and AM/PM controls. You can show optional minute labels. The screen also explains that an analog dial repeats every 12 hours. On first use the clock starts at 09:00, and after that it restores the last value.
- **Practice**: four topics at three difficulty levels. Each session has 10 questions and no timer. There are optional hints, a locally generated explanation after every answer, and one unfinished free session that can be resumed, ended or restarted.
- **Today's Practice**: one persistent set of 10 questions per local date.
- **History**: sessions, per-topic breakdowns, a full question review, and all-time totals that survive pruning.
- **Calculator**: a basic BigDecimal calculator that keeps the latest 50 calculations and has "Use result".
- **Settings**: default difficulty, 12/24-hour format, minute labels and reduced animation. Clearing calculations, progress or all data needs an adult check. There is also a bundled privacy screen.

## Architecture

The project is a single `:app` module with manual dependency injection (`AppContainer`).

```
com.mathclock.app
├── data/local        Room entities, DAOs, database (+Migrations), DataStore PreferencesStore
├── data/repository   PracticeStore (interface), RoomPracticeStore, PracticeService (business rules),
│                     PracticeRepository (IO dispatcher + change counter), CalculatorRepository, DataResetter
├── domain/calculator CalculatorEngine (pure state machine, BigDecimal)
├── domain/time       TimeMath (minutes-from-midnight arithmetic), ClockGeometry (hand angles, drag, snapping)
├── domain/generation Difficulty/Topic/Exercise, QuestionGenerator, DailyPlanner, ExerciseText (prompts/hints/explanations)
├── domain/progress   Scoring, SessionType/Status, injected Clock + DateProvider
└── ui/               board, clock (Canvas clock, Explore, controls), practice, results, history,
                      calculator, settings, common, theme, navigation
```

- Time arithmetic, hand geometry, question generation and scoring are pure Kotlin with no Android dependencies. The random source, wall clock and date provider are injected.
- The UI uses ViewModels with StateFlow, collected through `collectAsStateWithLifecycle`. Navigation uses Navigation Compose.
- All database work runs on `Dispatchers.IO`. Recording an answer, completing a session and updating the aggregates happen in a single Room transaction.

## Toolchain

| Component | Version |
|---|---|
| JDK | 17 (Temurin in CI; any JDK 17+ locally) |
| Gradle (wrapper, committed) | 8.14.3 |
| Android Gradle Plugin | 8.13.0 |
| Kotlin / Compose compiler plugin | 2.2.20 |
| KSP | 2.2.20-2.0.3 |
| Compose BOM | 2025.09.01 |
| Room | 2.8.1 (KSP, schemas exported to `app/schemas`) |
| Navigation Compose | 2.9.5 |
| Lifecycle | 2.9.4 |
| DataStore | 1.1.7 |
| core-splashscreen | 1.0.1 |
| compileSdk / targetSdk / minSdk | **36 / 36 / 26** |

All versions are pinned in `gradle/libs.versions.toml`. Never lower the SDK values to get around a build failure.

## Build commands

```bash
./gradlew testDebugUnitTest          # unit tests
./gradlew lintRelease                # release lint
./gradlew assembleDebug              # debug APK, no signing credentials needed
./gradlew assembleRelease bundleRelease   # signed release APK + AAB (credentials required)
```

Output locations:

- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`
- R8 mapping (only when minification is enabled): `app/build/outputs/mapping/release/mapping.txt`

## Calculator limits and rounding

- It handles one binary operation at a time (+ − × ÷). There is no expression parsing, no parentheses, no percentages and no scientific functions. Pressing an operator after a second operand first evaluates the pending operation.
- Operands and results must stay within −1,000,000 and 1,000,000. An operand can have up to 6 fractional digits. A digit that would break either limit is ignored.
- Division is rounded to 6 fractional digits with `HALF_UP`. A multiplication with more than 6 fractional digits is rounded the same way, so the result can still be used as an operand. Trailing zeros are removed. A rounded result is shown with **≈** and stored as `rounded = true`.
- Negative numbers are entered with **±**. Multiple decimal points are prevented and leading zeros are normalized.
- Pressing `=` again does not repeat the operation. A digit typed after a result starts a new calculation, while an operator continues from the result.
- Division by zero or an out-of-range result shows a friendly message and adds nothing to history.
- History keeps the latest 50 successful calculations. Selecting an entry shows **Use result**. Calculator use never affects learning accuracy.

## Time representation and clock interaction

- Every learning time is an integer number of minutes from midnight (`0..1439`). Exercise times are fictional, so no time zones or daylight-saving rules are involved. Values of 1440 or more only appear briefly, to mean "the next day" (for example the absolute answer of a Time After question).
- Minute hand angle = 6° × minutes. Hour hand angle = 30° × (hour mod 12) + 0.5° × minutes, so the hour hand moves continuously.
- Dragging works like this:
  - The whole dial is the touch target. The nearest hand by angle is picked when the drag starts; if the hands overlap, the distance from the centre decides. That hand stays selected for the whole gesture.
  - Minutes snap to the current precision (1, 5 or 30 minutes).
  - The minute hand always moves by the shortest signed amount, so crossing 12 carries into the next or previous hour without jumping.
  - Dragging the hour hand keeps the minute and the AM/PM half, so crossing 12 never flips AM/PM by itself. Only the AM/PM control changes AM/PM.
- Explicit Hour/Minute steppers (≥ 48 dp, labelled for TalkBack) and an AM/PM selector do the same job without dragging.

## Difficulty, daily generation and midnight rules

| Level | Clock precision | Time After | Minutes Between | Midnight crossing |
|---|---|---|---|---|
| Easy (default) | whole and half hours | 30 or 60 min | 30–120 in 30-min steps | never |
| Medium | 5 minutes | 5–120 in 5-min steps | 5–180 in 5-min steps | never |
| Hard | 1 minute | 1–180 | 1–240 | about 30% of duration questions, always labelled "the next day" |

- Multiple-choice questions always have exactly four distinct options with exactly one correct answer, in shuffled order. Values are normalized before the uniqueness check, and the visible labels are distinct as well. Wrong options come from common mistakes:
  - swapping the hour and the minute
  - the number under the long hand read as minutes
  - off by one hour
  - subtracting instead of adding
  - treating times as decimals (1505 − 1420 = 85)
  - comparing only the minutes
- Read the Clock always states which half of the day it is ("It is afternoon (PM).") and keeps every option in that half, so the analog dial never has to tell morning from evening by itself.
- Set the Clock compares the selected time as normalized minutes, snapped to the level's precision, never as hand coordinates. During the exercise there is no live digital readout, only the labelled hour and minute controls.
- Minutes Between keeps explicit `crossesMidnight` metadata, and an end time earlier than the start time is never assumed silently.
- **Daily set**:
  - It contains 2 questions of each topic plus 2 extra questions from two different topics, rotating with the date, all shuffled. The whole set, including the option order, is generated and saved before the first question is shown.
  - The local date (`yyyy-MM-dd`) is the unique key. Returning to a date that already has a set reuses it.
  - Changing the difficulty or format only affects the next set.
  - If midnight passes during a question, the child finishes it in the original set and is then offered the new day's set. The earlier set stays in history as incomplete, and its unanswered questions are never counted as errors.
  - Each answer stores `answeredLocalDate` once, so a later time-zone change does not rewrite daily totals.
  - There are no streaks, rankings or penalties. Deliberate changes to the device date are not detected.

## Persistence and recovery

- Room stores `CalculationEntity`, `PracticeSessionEntity` (unique non-null `dailyDate`), `TimeQuestionEntity` and `ProgressTotalsEntity`. Decimals are stored as canonical strings. DataStore stores preferences, the explored clock value, the last practice topic and the calculator draft.
- A first answer is recorded with `UPDATE … WHERE submittedAnswer IS NULL` inside a transaction, so double taps or process recreation cannot change or double-count it.
- The current question position is persisted. A pending selection and the Set the Clock hand position live in `SavedStateHandle`.
- Ending a session early keeps the answered questions. A free session with no answers is discarded and leaves no history entry.
- History keeps the latest 100 finished free sessions and the latest 90 daily sets. Totals (answered, correct, completed sessions, per-topic counts) are kept separately and survive pruning.
- The schema is at version 1 and exported to `app/schemas`. `Migrations.ALL` is empty because there is no earlier schema. Destructive migration is not enabled; every future version must add a migration.

## Offline operation, privacy, storage and backup

- The app declares no `INTERNET`, no `ACCESS_NETWORK_STATE` and no runtime permissions. There are no networking libraries, Firebase, ads, analytics, payments, external links or WebView.
- All data is in app-private storage. `android:allowBackup="false"`, `fullBackupContent` excludes every domain (Android 11 and lower), and `dataExtractionRules` excludes every domain from both cloud backup and device transfer (Android 12 and later).
- "Clear all local data" removes every table and resets preferences to their defaults.

## Permission verification

`scripts/check_permissions.sh <apk> [merged-manifest]` lists the permissions in the packaged APK with `aapt2` and fails on anything outside the allow-list. The only allowed entry is `com.mathclock.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which androidx.core adds. It is an app-private, signature-level permission that grants no capability. CI also prints the `uses-permission` entries of the merged release manifest.

## Release signing (PKCS12)

`app/build.gradle.kts` defines `signingConfigs.release` with `storeType = "PKCS12"` and assigns it to the `release` build type. Credentials are read from:

1. Environment variables `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`, which CI sets.
2. Otherwise, the git-ignored `keystore.properties` file (see `keystore.properties.example`).

If credentials are missing, `validateSigningRelease`, `packageRelease`, `signReleaseBundle` and `packageReleaseBundle` fail. Release builds never fall back to debug signing. Debug builds need no credentials. Never commit `*.p12`, `keystore.properties` or passwords.

GitHub Secrets:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 mathclock-release.p12` (macOS: `base64 -i mathclock-release.p12`) |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | `mathclock` |
| `ANDROID_KEY_PASSWORD` | key password |

**Keys and Play App Signing.** This keystore is the **upload key**. With Play App Signing, which is required for new apps, Google holds the **app signing key** that signs what users install. You sign every AAB with the upload key. If the upload key is lost, you can ask for an upload-key reset in Play Console, but keep encrypted backups of the `.p12` file and its passwords anyway.

## CI (`.github/workflows/android-release.yml`)

1. JDK 17, Android SDK Platform 36, build-tools 36.0.0, and the committed Gradle wrapper (validated).
2. `testDebugUnitTest` and `lintRelease`.
3. Decode the PKCS12 keystore into `$RUNNER_TEMP` (not on pull requests).
4. `assembleRelease bundleRelease`.
5. Verify the APK with `apksigner verify --print-certs`, and fail on `CN=Android Debug`.
6. Verify the AAB with `jarsigner -verify`, and compare its signer's SHA-256 with the keystore certificate. A self-signed upload certificate is accepted.
7. Check permissions, check 16 KB ELF alignment in the APK and AAB, and run `zipalign -c -P 16`.
8. Upload the verified APK, AAB and (when present) mapping file, then remove the temporary keystore.

Upload **only the `.aab`** to Google Play. Use the APK for local installation and checks.

## R8 and resource shrinking

Release builds are **not minified** for now (`mathclock.minify=false` in `gradle.properties`). The signed, non-minified release has to be verified first. After that, set `mathclock.minify=true` (or pass `-Pmathclock.minify=true`) and repeat these checks:

- calculator
- clock dragging
- question generation
- persistence and recovery
- navigation

Keep `mapping.txt`; CI uploads it.

## 16 KB page-size compatibility

The app has no native code of its own. Some dependencies may package native libraries; for example, recent Compose UI pulls in `androidx.graphics:graphics-path`, which ships `libandroidx.graphics.path.so`. CI runs `scripts/check_elf_alignment.py` on both the APK and the AAB. The script checks that every `PT_LOAD` segment has `p_align ≥ 16384`, and that any `.so` stored uncompressed in the APK starts at a 16 KB-aligned offset. It also runs `zipalign -c -P 16`. **Findings: pending.** The inspection has not run yet (see VERIFICATION.md). Targeting API 36 alone does not prove 16 KB compatibility. Before claiming runtime compatibility, also test on a 16 KB emulator image (Android 15+ "16 KB page size" system image).

## Local install and logcat checks

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat --pid=$(adb shell pidof -s com.mathclock.app) '*:W'
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## Android 16 (API 36) notes

- Edge-to-edge is always on: the app calls `enableEdgeToEdge()` and every screen handles system-bar and cutout insets.
- Predictive Back is enabled (`enableOnBackInvokedCallback="true"`), and only Navigation Compose back handling is used.
- On large screens, orientation and resizability locks are ignored. The app declares none and adapts its layout at a 700 dp width.
- The app does not keep the screen awake and does not use sticky immersive mode.

## Checks status

See [VERIFICATION.md](VERIFICATION.md) for which checks have been done and which are still pending.
