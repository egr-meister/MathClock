# Verification notes

Date: 2026-10-01

## Completed

| Check | Environment | Result |
|---|---|---|
| Domain and service unit tests: 64 tests covering calculator, time arithmetic, hand geometry, generation, explanations, daily sets, duplicate answers, date changes and pruning | Kotlin 2.2.20 compiler (`kotlinc`) on JDK 21, run with a minimal JUnit-compatible runner and an in-memory `PracticeStore` fake | **64 passed, 0 failed** |
| PKCS12 release keystore generated (RSA 4096, alias `mathclock`, valid 10,000 days) | `keytool` | Done. The file is kept outside the repository. |

The unit tests above did **not** run through Gradle. The sandbox has no access to Google Maven or Maven Central, so the Android/Compose/Room code was never compiled. The same test files run unchanged with real JUnit 4 under `./gradlew testDebugUnitTest`.

## Pending (not yet performed; must not be reported as passed)

- `./gradlew testDebugUnitTest lintRelease assembleRelease bundleRelease`: first full Gradle build. This includes compiling the Compose UI and Room, and exporting `app/schemas/…/1.json`, which should be committed after the first build.
- `apksigner verify --print-certs` on the APK, and the `jarsigner` signer check on the AAB.
- Permission check of the packaged APK and the merged manifest.
- 16 KB inspection of the APK and AAB (`scripts/check_elf_alignment.py`, `zipalign -c -P 16`), and a run on a 16 KB emulator image.
- Device checks with `adb install` and `adb logcat` for the signed release APK:
  - first launch in airplane mode, and no permission prompts
  - analog and digital stay in sync; dragging both hands; accessible controls
  - noon, midnight and AM/PM behaviour
  - all four topics at all three levels; daily practice; recovery after process death
  - calculator and its history
  - rotation, large fonts, TalkBack, Android Back
  - data reset
- R8 / resource shrinking: intentionally disabled until the steps above pass.

Tested device or emulator, Android version, artifact checksum: to be recorded after the first device run.
