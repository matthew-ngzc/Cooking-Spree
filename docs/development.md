# Development and test guide

Purpose: get a reproducible local build/test loop. Gradle configuration is authoritative; this is a compact operational aid.

## Prerequisites

- Android Studio with an Android SDK platform matching `compileSdk 35` and an emulator/device running API 34+ (the app `minSdk` is 34).
- A JDK supported by Android Gradle Plugin 8.10.1; Java 21 is installed in the inspected environment.
- Internet access may be required on the first Gradle build to resolve dependencies.
- Firebase-dependent paths require a valid `app/google-services.json` and backend configuration. Basic build work should not expose that file's contents.

## Emulator/device setup

Phase 0 includes an agent-owned [test environment checklist](../plans/phase-0-baseline-recovery.md#test-environment-setup--begin-with-0a). The agent first inspects installed SDK/JDK tools, attached devices, and existing AVDs, then reuses or creates a dedicated compatible test AVD. Compilation needs SDK platform 35; device tests need API 34+. An API 35 test image is the initial plan target, without requiring Google-account sign-in for guest/offline checks.

Android Studio is a convenient way to create/start a device: open Device Manager, create a phone AVD with an appropriate API 35 image if needed, and start it. It does not need to remain open when the emulator is launched independently through command-line tooling. See [AVD management](https://developer.android.com/studio/run/managing-avds) and [emulator command-line startup](https://developer.android.com/studio/run/emulator-commandline).

Before device tests, confirm the intended target appears in `adb devices -l` and has finished booting. Record its serial and API/image; explicitly choose the test target when multiple devices are connected. Do not reset an owner's phone or unrelated AVD to create clean test state. The agent should request owner action only for a demonstrated prerequisite it cannot resolve, such as required OS virtualization configuration or physical-device authorization.

Local unit tests run without an emulator. Device/instrumented tests and app smoke scenarios require one. Missing device access is pending verification, not a test pass.

## Test commands

Run Gradle from `Code/` in PowerShell. Run Git commands from the project root:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

The last command needs a running emulator or connected device. The committed unit/instrumented test classes are starter examples only; a green test task is build coverage, not gameplay coverage.

## Current verified build state

On 2026-09-21, `./gradlew.bat testDebugUnitTest --console=plain --no-daemon` reached Java compilation but failed before tests: `com.game.cookingspree.R` was missing generated nested resource classes such as `R.id`, `R.layout`, `R.string`, and `R.raw` (94 unresolved-resource errors). Resource symbols themselves appear in the generated `R.txt`, so this is a build/resource-generation integration problem, not evidence that every referenced resource is absent. Diagnose and restore `assembleDebug`/unit-test compilation before treating test results or manual gameplay validation as available.

The device test's expected package name is also stale (`com.example.com.game.com.game.cookingspree` rather than the configured `com.game.cookingspree`); correct it when making the test suite meaningful.

## Manual smoke test

1. Launch in landscape; verify the menu opens and Start Game creates the kitchen.
2. Hold a direction, verify tile movement/collision, then pick up an ingredient from a basket.
3. Cook a valid three-ingredient recipe, collect it, and submit it against a matching order.
4. Exercise table storage and rubbish disposal; let three orders expire and verify the game-over flow.
5. Pause/resume, save, return to menu, load, then confirm player/order/inventory/pot/table state is sensible.
6. If touching accounts/settings, repeat signed-out and failed-network paths.

## Where to put work

- Java source: `Code/app/src/main/java/com/game/cookingspree/`.
- Unit tests: `Code/app/src/test/`; device tests: `Code/app/src/androidTest/`.
- Android layouts/resources: `Code/app/src/main/res/`.
- Runtime tile/map assets: `Code/app/src/main/assets/`; update the Tiled authoring source deliberately too.

Do not place generated APKs, build directories, extracted dependencies, or device data under source folders.
