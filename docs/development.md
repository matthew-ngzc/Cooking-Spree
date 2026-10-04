# Development and test guide

Purpose: get a reproducible local build/test loop. Gradle configuration is authoritative; this is a compact operational aid.

## Prerequisites

- Android Studio with an Android SDK platform matching `compileSdk 35` and an emulator/device running API 34+ (the app `minSdk` is 34).
- A JDK supported by Android Gradle Plugin 8.10.1; Java 21 is installed in the inspected environment.
- Internet access may be required on the first Gradle build to resolve dependencies.
- Firebase-dependent paths require a valid `app/google-services.json` and backend configuration. Basic build work should not expose that file's contents.

## Emulator/device setup

Phase 0 includes an agent-owned [test environment checklist](../plans/phase-0-baseline-recovery.md#test-environment-setup--begin-with-0a). Compilation needs SDK platform 35; device tests need API 34+. On 2026-10-03, the inspected environment had JDK 21, SDK platform 35, build tools 35.0.0/35.0.1, ADB, and emulator 35.4.9. The owner-profile AVD list included `Medium_Phone_API_36` (Android 36) and `Pixel_8` (Android 34); no device was attached. No AVD was started or changed for the 0A build gate. An API 35 test image remains the initial plan target if a new AVD is needed later, without requiring Google-account sign-in for guest/offline checks.

Android Studio is a convenient way to create/start a device: open Device Manager, create a phone AVD with an appropriate API 35 image if needed, and start it. It does not need to remain open when the emulator is launched independently through command-line tooling. See [AVD management](https://developer.android.com/studio/run/managing-avds) and [emulator command-line startup](https://developer.android.com/studio/run/emulator-commandline).

Before device tests, confirm the intended target appears in `adb devices -l` and has finished booting. Record its serial and API/image; explicitly choose the test target when multiple devices are connected. Do not reset an owner's phone or unrelated AVD to create clean test state. The agent should request owner action only for a demonstrated prerequisite it cannot resolve, such as required OS virtualization configuration or physical-device authorization.

Local unit tests run without an emulator. Device/instrumented tests and app smoke scenarios require one. Missing device access is pending verification, not a test pass.

## Test commands

Run Gradle from `android/` in PowerShell. Run Git commands from the project root. The accepted directory naming decision is recorded in [ADR 0005](decisions/0005-android-project-directory.md):

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

The last command needs a running emulator or connected device. The committed unit and instrumented suites now include focused Phase 0 regressions, but a green task still does not replace the manual acceptance matrix.

## Current verified build state

On 2026-10-03, after Phase 0 slice 0B changes, `testDebugUnitTest` and `assembleDebug` succeeded from `android/`. The focused `SessionAndPersistenceTest` exercises null-user skipping, local-write survival after a simulated cloud failure, upload-free cache hydration, joystick selection ordering, and game-over stats/save clearing. Those automated checks used neither a live Firebase backend nor a device; the separate device run below covers the signed-out airplane-mode and restart-persistence behavior.

Also on 2026-10-03, `connectedDebugAndroidTest` passed on a fresh temporary-data `Medium_Phone_API_36` AVD (`emulator-5556`, API 36, x86_64, 1080×2400 at 420 dpi). With airplane mode enabled and no signed-in user, the menu and a new game launched successfully. Changing volume to `19` and joystick size to large (`1.4`) persisted across an app restart. Letting three orders expire produced one game-over path; local games played became `1`, average score remained `0`, and `GameSave` was empty after completion. The nonzero high-score calculation and simulated cloud failure remain covered by `SessionAndPersistenceTest`; no live backend was required.

On 2026-10-03, `clean`, `assembleDebug`, `testDebugUnitTest`, and `assembleDebugAndroidTest` completed successfully from `android/` using the configured Gradle 8.11.1 wrapper. The app and unit-test tasks succeeded after clearing generated build outputs, so they were rebuilt from tracked project inputs. The instrumentation APK compiled with the corrected expected package name `com.game.cookingspree`, matching the configured application ID. The build emitted non-fatal warnings for the manifest's legacy `package` attribute, deprecated API use, and unchecked operations. That clean 0A baseline did not itself run on a device; the subsequent API 36 device validation is recorded above and in [runtime architecture](architecture.md#concurrency-boundaries).

On 2026-10-04, after Phase 0 slice 0D, `testDebugUnitTest`, `assembleDebug`, and `assembleDebugAndroidTest` passed. `connectedDebugAndroidTest` passed 8/8 on `Medium_Phone_API_36` (API 36). The new device scenarios verify that tutorial movement does not resume order/cook/fetch work, manual pause survives a background/foreground cycle, a running game resumes after backgrounding, and active cooking and ingredient exchange remain unchanged through a 10-second pause before completing once after resume. A labelled screenshot sequence records the visible running, paused, background-return, explicit-resume, tutorial, movement-only, and post-skip states for the slice 0D PR.

Later on 2026-10-04, after Phase 0 slice 0E, `testDebugUnitTest`, `assembleDebug`, and `assembleDebugAndroidTest` passed with 28/28 unit tests. `connectedDebugAndroidTest` passed 13/13 with no skips or failures on `Medium_Phone_API_36` (API 36). The expanded device suite verifies rich legacy restoration, invalid-load isolation, repeated non-consuming DONE-pot saves, a COOKING load/save/reload that resumes and yields one collectible item, failed-capture preservation of the previous save, and loaded-game save clearing. The parser unit suite covers malformed types, counts, IDs, states, times, coordinates, cooking progress, recipe names, terminal saves, and map-count mismatches. No live backend is involved.

On 2026-10-05, the ADR 0007 save amendment passed `testDebugUnitTest`, `assembleDebug`, and `assembleDebugAndroidTest` from `android/`; the unit suite contains 32 tests. `connectedDebugAndroidTest` then passed 17/17 with no skips or failures on `Medium_Phone_API_36` (`emulator-5554`, API 36). The regressions cover strict semantic-version compatibility, saved/current version copy, stable recipe identities, same-major balance preservation, committed-tile snapshots, readback-verification rejection, blocking corruption/version rejection, cleanup only after acknowledgment, no exposed partial session on application failure, terminal restoration, `Waste`, off-order dishes, and rich session round trips. [Three touch-visible recordings](https://github.com/matthew-ngzc/Cooking-Spree/pull/6#issuecomment-5984271004) show successful verified save/load, incompatible `2.4.1` versus current `1.0.0` rejection, and same-version corruption rejection. No live backend was involved.

Later on 2026-10-05, the owner replaced active-payload rollback with two-slot promotion. `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passed with 32/32 unit tests, and `connectedDebugAndroidTest` passed 18/18 on `Medium_Phone_API_36` (`emulator-5554`, API 36). The expanded device regressions cover an empty initial store, first promotion, pre-two-slot `GameSave` adoption, candidate-write failure, verifier failure, selector-promotion failure, unchanged and loadable prior data, A/B alternation, compatibility-store retirement, failed live capture, player-facing failure copy, touch-driven menu/load transitions, and clearing every store on completion. [A concise six-second touch-visible recording](https://github.com/matthew-ngzc/Cooking-Spree/pull/6#issuecomment-5984709101) shows the failed-save explanation followed by loading the prior score of `111`.

Later on 2026-10-04, after Phase 0 slice 0F, `assembleDebug testDebugUnitTest assembleDebugAndroidTest` completed successfully with 32/32 unit tests. `connectedDebugAndroidTest` passed 14/14 with no skips or failures on `Medium_Phone_API_36` (emulator-5556, API 36). The new regressions cover exact recipe multisets and duplicate potatoes, wrong/missing/extra ingredients, deterministic one-second expiry, the inclusive ten-second streak boundary and reset, and the three-failure finalization gate. Device checks repeated tutorial activity entry/exit five times, retained the existing five `GameView` surface recreation cycles, and drove three real expiries in both new and loaded sessions to verify one stats update and `GameSave` clearing. No live Firebase/backend path was part of the scenarios.

The final API 36 smoke pass also launched the app signed out with airplane mode shown, reopened persisted large-joystick settings, entered a fresh kitchen, exercised held tile movement/collision plus basket, table, and rubbish interactions, retained the visible pause menu across a background/foreground cycle, and reached one natural game-over dialog at exactly 3/3 failures. The before/after paused frames were byte-identical, confirming no visible scene progress while backgrounded. Earlier 0D and 0E evidence supplies the tutorial, active cook/fetch pause, and save/load/recovery sequences. A clean successful-submission screenshot could not be captured before the randomized short order queue expired; exact valid/invalid recipe classification, duplicate ingredients, scoring, and terminal finalization are nevertheless covered by the deterministic 32-unit/14-device suites. That visual omission is explicit in the Phase 0 plan and PR rather than being reported as a manual pass.

## Manual smoke test

1. Launch in landscape; verify the menu opens and Start Game creates the kitchen.
2. Hold a direction, verify tile movement/collision, then pick up an ingredient from a basket.
3. Cook a valid three-ingredient recipe, collect it, and submit it against a matching order.
4. Exercise table storage and rubbish disposal; let three orders expire and verify the game-over flow.
5. Pause/resume, save, return to menu, load, then confirm player/order/inventory/pot/table state is sensible.
6. If touching accounts/settings, repeat signed-out and failed-network paths.

## Where to put work

- Java source: `android/app/src/main/java/com/game/cookingspree/`.
- Unit tests: `android/app/src/test/`; device tests: `android/app/src/androidTest/`.
- Android layouts/resources: `android/app/src/main/res/`.
- Runtime tile/map assets: `android/app/src/main/assets/`; update the Tiled authoring source deliberately too.

Do not place generated APKs, build directories, extracted dependencies, or device data under source folders.
