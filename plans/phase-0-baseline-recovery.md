# Phase 0 — Baseline recovery

[Overarching Android roadmap](android-roadmap.md) · Previous phase: none · [Next: Phase 1](phase-1-competition.md)

## Contract and evidence

**Outcome:** a reproducibly buildable Android app whose existing single-player loop can be played, paused, saved, loaded, and ended while signed out and offline, without the validated crashes, destructive saves, duplicate tutorial sessions, or abandoned background work.

**Status:** implementation authorized by the owner on 2026-10-03. ADR 0003 was accepted by the owner on 2026-10-04, unlocking its dependent slices 0D and 0E.

## Phase progress

- [ ] Owner approves this Phase 0 implementation contract. Implementation was requested on 2026-10-03, but this owner-only checkbox remains for the owner to mark directly.
- [x] Owner accepts ADR 0003 before slices 0D and 0E. Evidence: the owner explicitly accepted ADR 0003 on 2026-10-04.
- [x] Owner accepts ADR 0007's versioned save-compatibility contract. Evidence: on 2026-10-05 the owner selected semantic versions with the major number as the incompatibility boundary, destructive rejection of incompatible/corrupt saves after a visible error, last-committed-tile capture, and atomic save/load behavior.
- [x] 0A — Verify and stabilise the build. Evidence: clean wrapper build and unit tests passed on 2026-10-03; instrumentation APK compiled after correcting the application-package assertion. See [development verification](../docs/development.md#current-verified-build-state).
- [x] 0B — Signed-out and unavailable-cloud safety. Evidence: focused tests plus the API 36 signed-out/airplane-mode launch, settings restart, and natural game-over run recorded in [development verification](../docs/development.md#current-verified-build-state).
- [x] 0C — Single session, cancellation, and render/input teardown. Evidence: focused unit/device lifecycle checks and five repeated actual surface cycles passed; see [runtime architecture verification](../docs/architecture.md#concurrency-boundaries).
- [x] 0D — Consistent pause and tutorial lifecycle. Evidence: focused unit tests and the API 36 8/8 connected suite passed on 2026-10-04, including independent pause reasons, tutorial movement-only allowance, manual/background resume behavior, and a 10-second active cook/fetch pause; labelled device screenshots were captured for the PR.
- [ ] 0E — Versioned, verified saves and atomic loading. The owner-required two-slot amendment passes 32/32 unit tests and the API 36 18/18 connected suite; its concise updated recording is captured and awaits PR upload.
- [ ] 0F — Integration evidence and delivery.
- [ ] Owner approves and merges each Phase 0 PR.
- [ ] Owner approves Phase 0 closeout after all delivery-map PRs are merged and the phase evidence is complete.

## PR delivery map

Each PR becomes ready for review when its own gate below is met. After making it ready, continue with the next PR without waiting for owner review or merge unless a listed blocker applies. When its predecessor is still unmerged, create the next PR as a stack based on the predecessor branch; after the predecessor merges, retarget or rebase it onto the normal integration branch. Owner review is required for every PR, and the agent must not merge them.

| PR | Assigned scope | Base and mergeability | Ready-for-review gate | Next work |
| --- | --- | --- | --- | --- |
| 1 — baseline and session safety ([current PR #2](https://github.com/matthew-ngzc/Cooking-Spree/pull/2)) | 0A–0C plus the decisions and delivery documentation created with that work | Targets the normal integration branch and can merge independently | 0A–0C gates remain satisfied; docs and commit walkthroughs are current; required lifecycle/device evidence is attached or any unavailable evidence is explicit | Begin PR 2 without waiting unless review could change the 0D contract |
| 2 — pause and tutorial lifecycle | 0D | Depends on PR 1; stack on PR 1 while it is unmerged, then retarget/rebase after PR 1 merges | 0D tasks, tests, gate, owning docs, walkthroughs, and API 34+ device evidence are complete | Begin PR 3 without waiting unless 0D findings or owner feedback change persistence assumptions |
| 3 — versioned and verified save/load ([current PR #6](https://github.com/matthew-ngzc/Cooking-Spree/pull/6)) | 0E plus the accepted ADR 0007 amendment | Targets `main` after its predecessors merged; can merge independently once the reopened gate passes | Original 0E evidence remains recorded, and every amendment task below, focused tests, owning docs, walkthroughs, and API 34+ touch-visible save/load/error evidence are complete | Restack PR #7 after this amended branch is ready |
| 4 — integration and phase closeout | 0F and the complete manual acceptance matrix | Depends on PRs 1–3; stack on the latest predecessor while any remain unmerged | 0F tasks and gate are complete; full Phase 0 evidence, documentation, deferred risks, and final walkthroughs are current | Await owner review/merges and the separate Phase 0 closeout approval |

Blocking actions are limited to an unresolved owner/product or ADR decision, a genuinely unavailable device/external prerequisite, predecessor feedback likely to invalidate the next slice, or an inability to isolate the stacked diff safely. Ordinary pending review or merge is not a blocker.

Inputs:

- [Corrected repository review](../docs/reports/2026-09-21-repository-review.md), checked against source on 2026-10-01.
- [Accepted direction](../docs/direction.md), [ADR 0001: delivery](../docs/decisions/0001-agent-governance-and-delivery.md), and [ADR 0002: offline-first direction](../docs/decisions/0002-offline-first-competition-direction.md).
- [Existing Phase 0 outline](../docs/roadmap.md): its build, test, offline, smoke-test, and owner-review criteria are incorporated here; this file supplies the missing bounded slices.
- [Accepted ADR 0003](../docs/decisions/0003-phase-zero-session-and-save-boundaries.md): session ownership, pause behavior, and legacy-save compatibility. Accepted by the owner on 2026-10-04.
- Inbox context: [account/local play](../docs/ideas/inbox.md#account-local-play-and-cloud-save), [preference validation](../docs/ideas/inbox.md#preference-migration-and-account-validation), and [game quality/tutorial](../docs/ideas/inbox.md#game-quality-and-tutorial). Only the defect recovery described here is proposed; broader inspirations remain uncommitted.

## Scope and explicit deferrals

Include all report P1 findings, render/input/session cleanup, manual/lifecycle/tutorial pause consistency, safe loading of the existing save format, focused regression tests, and removal of raw profile logging. Repair small adjacent defects only when necessary to meet a listed acceptance criterion; report unrelated discoveries without silently expanding scope.

Defer to Phase 1: backend/rules validation, first-account sign-in redesign, local/cloud conflict UI, Chef Code schema/uniqueness/following fixes, numeric cloud hydration, leaderboards, and account data migration. Only signed-out safety, failure containment, removal of static Activity retention, and logging cleanup touch account code here. Do not present existing account sync as production-ready or change cloud conflict policy implicitly.

Defer to Phase 2: action-gated tutorial redesign, basket/fetch/streak save continuity, stable map/recipe IDs and a new versioned save schema, responsive map/layout changes, balance, catalogue-wide refactoring, and rendering optimization. Phase 0 validates the current map/catalogue and legacy keys; it does not promise compatibility with future renamed recipes or reordered maps. Basket selection is regenerated on load and streak resets remain documented limitations.

No iPhone, multiplayer, monetisation, artwork/audio replacement, SDK/platform expansion, backend deployment, or production publishing. Preserve Firebase configuration without reproducing it in logs, docs, commits, or handoff output.

## Execution and ownership

Sol/Terra orchestrates; **Luna implements**. Execute slices 0A–0F sequentially because they overlap in `GameActivity`, session timing, and tests. Do not send the entire phase as one unchecked rewrite or run overlapping mutations in parallel.

For each slice, the orchestrator gives Luna the slice ID, required readings, bounded files, acceptance criteria, predecessor result, and known limitations. Luna returns changed files, rationale, checks and results, documentation updates, and unresolved risks. The orchestrator inspects the changes, resolves integration concerns, runs the relevant checks, and advances only when that slice's gate is met. A blocked device check may leave implementation reviewable but cannot be recorded as a pass or phase completion.

Use the `gpt-6-luna` agent for implementation when execution is requested. No agent or separate chat is launched by this planning step. Read the project-root `AGENTS.md` before execution.

The project root is the Git repository and `android/` is the Android Gradle project, as recorded in [ADR 0005](../docs/decisions/0005-android-project-directory.md). Run Git from the root and Gradle from `android/`. Before mutation, inspect the branch, status, and applicable instructions; preserve unrelated changes. Do not claim the tree is clean without checking it. Canonical plans and docs are in this repository and are included in the same PR as implementation changes.

## Test environment setup — begin with 0A

Environment setup is part of execution, not an assumed owner prerequisite. The agent performs the following preflight before relying on test results and prepares device access early enough for lifecycle tests in 0C onward:

1. [x] Locate the actual Android SDK and supported JDK; verify Java/Gradle compatibility, SDK platform 35 for compilation, platform-tools/ADB, and emulator tooling. Evidence (2026-10-03): JDK 21, SDK platform 35, build tools 35.0.0/35.0.1, ADB, emulator 35.4.9, and the pinned Gradle 8.11.1 wrapper are installed. A sandboxed invocation initially could not fetch the wrapper distribution; the configured wrapper succeeded when run with normal Gradle cache/network access.
2. [x] Check attached devices with `adb devices -l` and list available virtual devices using the installed emulator-management tool. Evidence (2026-10-03): no attached devices; the owner-profile AVD list includes `Medium_Phone_API_36`, `Medium_Tablet`, `Pixel_8`, `Pixel_Tablet`, and `exploring_koufu_app`.
3. [x] Identify a suitable development AVD. Evidence: existing `Medium_Phone_API_36` is listed and an Android 36 x86_64 Google APIs system image is installed; no AVD was created, started, or modified for the 0A gate. API 34+ meets the app's runtime requirement. No Google-account login is required for Phase 0 guest/offline tests. Do not wipe an existing personal device or unrelated AVD.
4. [x] Start the selected AVD through command-line tooling, or use an owner-started device. Evidence (2026-10-03): a fresh temporary-data `Medium_Phone_API_36` instance booted headlessly as `emulator-5556`, API 36/x86_64, at 1080×2400 and 420 dpi. The existing AVD user data was not wiped or overwritten.
5. [ ] Complete build, device, and reviewable evidence capture. The 0A build commands and 0B/0C device scenarios passed on 2026-10-03. PR #2 includes labelled 0A–0C lifecycle evidence; slices 0D and 0E produced their API 36 automated/device evidence on 2026-10-04. Final 0F integration evidence remains pending.
   - [x] With the selected device ready, run `gradlew.bat connectedDebugAndroidTest`. Evidence: the package assertion and 0C lifecycle suite passed on `emulator-5556` on 2026-10-03; signed-out/offline menu, settings persistence, new-game launch, and game-over persistence were also exercised. On 2026-10-04, the expanded suite passed 13/13 on `Medium_Phone_API_36` (API 36), including the 0D lifecycle and 0E save/load scenarios. Final 0F evidence remains pending.
6. [x] Record the reproducible launch/test commands and setup in [development.md](../docs/development.md). Local unit tests do not need a running emulator. Automated device scenarios must assert outcomes, not merely launch the app; use a small test harness for canvas game state alongside tests through real Android controls.

The 2026-10-03 preflight found no attached device but did find existing API 34/36 AVDs in the owner profile. Recheck availability before device testing. If SDK downloads, license acceptance, hardware virtualization, Windows features, or device authorization actually require owner action, report the specific missing prerequisite and steps. Do not mark device verification passed until it ran.

Setup references: [Android virtual device management](https://developer.android.com/studio/run/managing-avds), [command-line emulator startup](https://developer.android.com/studio/run/emulator-commandline), and [Android testing fundamentals](https://developer.android.com/training/testing/fundamentals).

## 0A — verify and stabilise the build

**Dependencies:** none. **Files:** `android/app/build.gradle.kts`, project Gradle configuration/wrapper, version catalogue, and only source/resource files demonstrated to cause compilation failure. Documentation: `docs/development.md`.

Tasks:

1. [x] Run `assembleDebug` and `testDebugUnitTest` from tracked inputs. Evidence: both wrapper tasks succeeded before and after `clean` on 2026-10-03; the clean build recompiled app sources. The historical 94-error resource-symbol failure did not recur.
2. [x] Check for a demonstrated configuration/input failure. None was reproduced; no build configuration, dependency, platform level, Firebase configuration, or application ID change was needed.
3. [x] Correct the stale instrumented application-package assertion to `com.game.cookingspree`. Evidence: `assembleDebugAndroidTest` compiled and packaged successfully. The current Android/API boundary is unchanged.
4. [x] Run `gradlew.bat assembleDebug` and `gradlew.bat testDebugUnitTest` from `android/` after `clean`. Both succeeded without manually editing generated outputs. `assembleDebugAndroidTest` also passed to verify the changed test source.

- [x] **0A gate:** both commands succeeded repeatedly from tracked/configured inputs; no project input failure was reproduced. Starter unit-test success is only a build gate, not gameplay evidence. Device/gameplay evidence remains pending for later slices.

## 0B — signed-out and unavailable-cloud safety

**Dependencies:** 0A. **Files:** `BaseActivity.java`, `MainActivity.java`, `AccountManager.java`, `util/PrefsHelper.java`, `GameActivity.java` stat completion paths, and focused tests under `android/app/src/test/` or `androidTest/`. Java paths are under `android/app/src/main/java/com/game/cookingspree/`. Documentation: `docs/persistence.md`.

Tasks:

1. [x] Make local preference/stat writes succeed independently of authentication and Firestore tasks. Obtain the current user once per update; skip cloud writes when absent and attach bounded, non-sensitive failure reporting when present. Avoid synchronously waiting for network operations. Evidence: `LocalFirstWrite` null-user and simulated cloud-failure tests; writes use Firestore task failure listeners without waiting.
2. [x] Make the initial joystick selection reflect the saved setting without treating programmatic hydration as a user edit. Verify both menu and game launch. Evidence: both layouts call the shared pre-listener hydrator; unit test confirms saved large selection precedes listener installation with no upload.
3. [x] Remove the static reference chain from `PrefsHelper` to an Activity. Use application-context storage and an Activity-free sync boundary, keeping credential prompts/dialogs activity-owned. Avoid replacing the leak with a static callback that captures the Activity. Evidence: `PrefsHelper.init` stores `getApplicationContext()` preferences and `AccountManager.createSyncAdapter` captures only Firebase auth/database objects.
4. [x] Contain Firebase initialization/task failures so guest gameplay/settings remain available. Inspect initialization separately from network failure; do not swallow arbitrary gameplay exceptions as an offline workaround. Evidence: initialization is guarded in `BaseActivity`/`AccountManager`; cloud task failures are logged by exception type only and the offline-safe seam passes deterministic tests.
5. [x] Remove preference dumps and all identified raw chef-name logging. Preserve useful redacted diagnostics. Evidence: source search found no `PrefsDump` or chef-name log statements; profile and sync failure logs report exception type only.

- [x] **0B gate:** evidence (2026-10-03): on fresh temporary API 36 emulator data, signed-out airplane-mode menu/game launch succeeded; volume `19` and large joystick `1.4` survived restart; three natural expiries produced one game over, games played `1`, average score `0`, and an empty `GameSave`. `SessionAndPersistenceTest` covers a nonzero high score and simulated cloud failure. Source review confirms the static preference owner retains application storage and an Activity-free adapter, and source/log review found no raw preference/profile values in the removed paths.

- [x] **0B tests:** null-user update, failed-sync/local-write independence, initial selection without upload, and game-over stats/save clearing. Evidence: `SessionAndPersistenceTest` passed all 5 tests with a fake sync/local stats seam and no live backend, as recorded in [development verification](../docs/development.md#current-verified-build-state).

## 0C — single session, cancellation, and render/input teardown

**Dependencies:** 0B. **Files:** `GameActivity.java`, `TutorialActivity.java`, `GameManager.java`, `GameView.java`, `Game.java`, `Player.java`, `Pot.java`, `PotFunctions.java`, `PotThreadPool.java`, `IngredientFetchWorker.java`, `IngredientBasketFiller.java`, `IngredientQueue.java`; touch only responsibilities needed for session ownership. Documentation: `docs/architecture.md`.

Tasks:

- [x] Select the normal/tutorial layout before composing components. Initialize exactly one manager, render binding, pot pool, and fetch/fill chain per active activity session; prevent tutorial superclass setup from creating a hidden first game. Evidence: `TutorialActivity` selects its layout through the superclass hook; the connected tutorial-entry test confirmed one composition and a manager close on two entry/exit cycles on API 36.
- [x] Expose idempotent close/cancel operations. Reject work after closure, stop handlers, interrupt sleeping/queue-waiting work, and exit interrupted tasks before food creation, basket mutation, or callbacks. Dispose the filler through its owning fetcher. Evidence: focused tests cover repeated callback-gate closure, interrupted cooking without food/callback, and an interrupted blocked consumer; source review confirms the fetcher closes its owned filler/queue and the pool shuts down with interruption.
- [x] Replace the pot-to-`GameActivity` cast with a narrow listener. Suppress queued UI callbacks after session closure as well as worker callbacks; a main-thread runnable already posted needs the same ownership check. Evidence: `Pot` receives `PotFunctions.PotListener`; the callback-gate regression test executes an already-posted callback after closure and observes no call.
- [x] Give the render loop safely published state, null/init protection, prompt interrupt exit, and bounded teardown. Prevent a new surface from launching a second loop while the old one remains active; do not hold game-state locks while joining workers. Evidence: `GameView` uses volatile references, refuses duplicate live loops, checks null state, and joins for at most 300 ms; `testDebugUnitTest` and `assembleDebug` passed. The connected API 36 scenario detached and reattached the actual surface five times and observed at most one concurrent render loop.
- [x] Cancel all held-direction callbacks on pause/stop/destruction and handle simultaneous direction touches without orphaning a self-reposting runnable. Reset held movement at the same boundary. Evidence: per-control runnable tracking and lifecycle cancellation are in `GameActivity`; the two-direction release regression test confirms the remaining held direction stays active.

- [x] **0C gate:** repeated game/tutorial entry/exit leaves one active session and no old spawning/ticking/fetching/cooking work. Closing during cooking, fetching, or a blocked queue produces no late session mutation or UI callback. Surface recreation leaves one render loop; teardown has a defined bound and does not hang the UI. Evidence: on `emulator-5556` (Medium_Phone_API_36, API 36), the connected suite passed 5/5 on 2026-10-03. It repeated tutorial entry/exit twice, closed separate game sessions during an active fetch and cook within the five-second owner bound, checked ingredient/order snapshots and delivered UI callbacks after closure (including beyond the 13-second maximum order-spawn delay and six-second cooking duration), closed a blocked filler consumer in unit coverage, and detached/reattached the actual surface five times while observing one concurrent render loop and bounded teardown.

- [x] **0C tests:** idempotent closure, interrupted cook without food/callback, interrupted consumer without continuation, closed-owner callback suppression, two-direction press/release, and tutorial initialization count. Evidence: all 13 `SessionAndPersistenceTest` cases passed, including interruption of resumed cooking; `connectedDebugAndroidTest` passed 5/5 on `emulator-5556` (Medium_Phone_API_36, API 36), including two tutorial cycles, active fetch/cook closure, and five surface recreations. No garbage-collection timing is used as cleanup evidence.

## 0D — consistent pause and tutorial lifecycle

**Dependencies:** 0C and acceptance of ADR 0003. **Files:** session/lifecycle paths in `GameActivity.java`, `TutorialActivity.java`, `GameManager.java`, `Game.java`, `Player.java`, worker timing in `PotFunctions.java` and `IngredientFetchWorker.java`, and `ElapsedTimer.java` if needed. Documentation: `docs/gameplay.md`, `docs/architecture.md`.

Tasks:

1. [x] Track manual-menu pause, background pause, tutorial pause, and terminal/closed state independently. Gameplay runs only when no pause reason applies. Returning to the foreground removes only the background reason; it cannot resume a manual menu or tutorial pause. Evidence: `PauseState` unit coverage and the API 36 manual/background instrumentation scenario passed on 2026-10-04.
2. [x] Freeze order countdown/spawning, player progression/input, cooking, and ingredient fetch/fill transitions at pause. Preserve remaining in-memory work rather than completing it immediately or restarting it on resume. Rendering may continue to show the paused scene. Make boundary transitions safe when a worker tick and pause arrive together. Evidence: serialized-boundary unit coverage and the API 36 10-second cook/fetch/order pause scenario passed on 2026-10-04.
3. [x] Keep UI/menu visibility consistent with pause state. Prevent ordinary gameplay interaction while paused or over. Preserve the existing tutorial's intended movement demonstration through an explicit allowed step; do not redesign the tutorial. Evidence: movement-only allowance tests plus labelled manual-pause/tutorial screenshots on API 36.
4. [x] Initialize tutorial steps before checking their count, hide the ordinary pause button during the walkthrough, and prevent inherited `onResume` from undoing tutorial pause. Restore normal controls after skip/completion. Evidence: the API 36 tutorial instrumentation scenario and labelled start/movement/skip screenshots passed on 2026-10-04.
5. [x] Make game over terminal and idempotent: one dialog, one local stat update, one save clear; remaining callbacks and multiple expiries in one update cannot finalize the game repeatedly. Evidence: manager terminal guard plus `FinalizationGate` repeated-callback unit coverage.

- [x] **0D gate:** pause for at least 10 seconds while cooking and swapping; order/cook/fetch/player state is unchanged after pause settles. Resume continues once with remaining time. Backgrounding while manually paused returns to a visible paused menu. Backgrounding a running game resumes safely. Tutorial starts paused and creates no hidden orders; skip/completion produces one playable session. Evidence: `connectedDebugAndroidTest` passed 8/8 on `Medium_Phone_API_36` (API 36) on 2026-10-04; the new scenarios cover tutorial movement/skip, combined manual/background lifecycle, and a 10-second active cook/fetch pause. Labelled screenshots show the visible pause and tutorial states.

- [x] **0D tests:** combined pause reasons, remaining duration across pause, resume idempotence, tutorial first resume, multiple expiries/finalization once. Control time with a fake clock where practical; avoid sleep-heavy unit tests. Evidence: `SessionAndPersistenceTest` covers independent reasons, active-duration remainder, duplicate resume, tutorial/background behavior, serialized callbacks, and one-shot finalization; the connected suite supplies the required real 10-second lifecycle interval.

## 0E — non-destructive saves and validated legacy loading

**Dependencies:** 0D and acceptance of ADR 0003. **Files:** save/load paths in `GameActivity.java`, `Pot.java`, `PotFunctions.java`, `Player.java`, `GameManager.java`, `Order.java`, and `PrefsHelper.java`; small snapshot/parser/validator classes may be added under the existing package. No runtime/authoring map edits or catalogue renames. Documentation: `docs/persistence.md`, `docs/gameplay.md` where behavior changes.

Tasks:

1. [x] Snapshot completed food without consuming it. Initialize cooking recipe/progress before publishing COOKING; loaded cooking must restore both before workers resume. Snapshot each pot consistently rather than separately observing state, ingredients, recipe, and food. Evidence: atomic pot snapshot/restore seams plus repeated-DONE and near-complete-COOKING device scenarios passed.
2. [x] Save only through the paused boundary, serialize a validated complete snapshot, and write existing keys in one editor operation. A failed capture/write keeps the previous save and current game intact; display a single success or failure result, not the current unconditional extra success toast. Evidence: the API 36 failed-capture scenario retained the prior preference map, and the save action now emits one result.
3. [x] Capture a stable logical movement tile rather than a transient interpolated coordinate. Keep live paused state unchanged. Validate legacy coordinates; normalize a fractional legacy location only to a valid nearby traversable tile, otherwise reject safely. Explain this compatibility behavior in persistence documentation. Evidence: snapshot code records the target tile while moving; unit coverage accepts only a nearby traversable normalization.
4. [x] Parse all legacy values into a candidate state before mutating live gameplay. Bound counts against catalogue/current map/capacities; validate field types, positions/collision, nonnegative score, failures below game-over threshold, ingredient IDs, known recipe names (including Waste where legitimate), pot state/content/progress consistency, and order times. A finished/dead save must not invoke game-over during partial restoration. Evidence: `GameSaveParserTest` exercises invalid types, counts, IDs, states, times, coordinates, progress, recipes, terminal saves, and map counts.
5. [x] Apply a validated candidate before starting its orders/cooking workers, with the fresh game's timers/refill tasks unable to race restoration. Reject an invalid candidate without a partially loaded session; preserve stored data, show a recovery message, and offer a new game. Do not silently turn unknown recipes into Waste or drop saved orders. Evidence: the session `LOAD` pause and API 36 valid/rejected restore scenario proved worker isolation and unchanged stored invalid data.
6. [x] Retain current key names, map list ordering, and valid legacy saves. Test legacy Waste pots and partially filled EMPTY pots explicitly. Schema versioning, stable object/recipe IDs, basket/fetch/streak continuity, and automatic destructive migration are deferred. Evidence: legacy-map round-trip tests cover Waste and one/two-ingredient EMPTY pots; the limitations remain documented in `docs/persistence.md`.

- [x] **0E gate:** saving twice with DONE food leaves it collectible exactly once. Load a COOKING save, save again before completion, then reload and collect once. EMPTY pots with one/two ingredients, held items, table items, orders, score, and failures survive a valid round trip. Failed saves retain the last valid save. Malformed, unknown-recipe, out-of-range, and completed-game saves never partially restore or award stats. Ending a loaded game clears its save and subsequent Load reports no save. Evidence: all listed paths passed in the 13/13 API 36 connected suite on 2026-10-04; parser rejection paths also passed in the 28/28 unit suite.

- [x] **0E tests:** DTO/legacy-parser validation with wrong types/counts/IDs/states/times/coordinates, non-consuming snapshots, resumed-recipe snapshot, repeated save/load, recovery without mutation, and game-over save clearing. Test map-count mismatch rejection; reordered maps with identical counts remain a documented limitation until stable IDs are introduced. Evidence: `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passed with 28/28 unit tests, and `connectedDebugAndroidTest` passed 13/13 with no skips or failures on `Medium_Phone_API_36` (API 36) on 2026-10-04.

### 0E amendment — semantic save compatibility and verified recovery

The owner superseded the unversioned legacy-load policy on 2026-10-05. Preserve the original evidence above as history, but do not consider PR #6 ready until all amendment items are complete.

- [x] Use three-part semantic `versionName` values, beginning with `1.0.0`. Store the complete producing version in every save and compare semantic major versions for compatibility: equal majors are compatible; missing, older-major, newer-major, or malformed versions are incompatible. Cosmetic releases such as skins do not change the major version; an incompatible map, schema, catalogue identity, or saved-rule change does.
- [x] Save only the player's last fully reached traversable tile. Capture each pot and the rest of the paused session atomically, canonicalize valid `Waste` independently of active orders, validate before writing, synchronously persist, read back, and validate the stored snapshot before showing `Game saved`. Any failure is reported at save time and preserves the previous verified save.
- [x] On load, distinguish incompatible-version data from same-major corrupt data. Show an error that includes the saved and current full versions for incompatibility; show a corruption error for same-major malformed/inconsistent data. After acknowledgment, clear the rejected save and return to the menu without starting fresh gameplay or partially applying state.
- [x] Construct and validate the complete restored session while the `LOAD` pause blocks timers/workers, publish it only after all fields and continuations are ready, and close the activity instead of exposing a partially restored session when application fails. A terminal same-version save restores/finalizes the completed result exactly once rather than becoming playable.
- [x] Use stable persisted ingredient/item/recipe identities and derive display names from the current catalogue. Same-major balance changes preserve the saved order limit/remaining time; any release that cannot interpret existing identities or state must increment the compatibility major before shipping. Cooking a dish without a matching active order and cooking `Waste` remain valid.
- [x] Replace legacy invalid-load tests with focused version compatibility, saved/current version copy, corruption clearing, no-fresh-session failure, committed-tile, verified-readback, terminal restoration, `Waste`, off-order cooking, and atomic-application regressions. Evidence: `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passed with 32 unit tests, and `connectedDebugAndroidTest` passed 17/17 on `Medium_Phone_API_36` (API 36) on 2026-10-05.
- [x] Update ADR 0003/0007, persistence, gameplay, architecture, development evidence, and PR #6's walkthrough/changelog/limitations. Evidence: [the PR walkthrough and three touch-visible recordings](https://github.com/matthew-ngzc/Cooking-Spree/pull/6#issuecomment-5984271004) identify the `Medium_Phone_API_36` API 36 emulator and cover successful save/load, incompatible-version rejection, and same-version corruption rejection.

- [x] **Amended 0E gate:** `Game saved` is shown only after stored readback passes; that same-major snapshot resumes from its committed tile with orders/items/pots intact. Incompatible and corrupt saves show distinct errors, are cleared after acknowledgment, and never expose a fresh or partially restored session. Terminal state finalizes once; valid off-order dishes and `Waste` round-trip. Evidence: 32 unit tests, the API 36 17/17 connected suite, current owning docs, and the linked touch-visible PR recordings/walkthrough.

### 0E two-slot amendment — failed attempts have no effect

- [x] Owner selects two-slot promotion: the active save payload must never be overwritten by an attempted save. A failed attempt means that the previous save remains available exactly as it was; a failed first attempt creates no save.
- [x] Stage each candidate in the inactive A/B slot, synchronously read back and fully validate it, then promote only the small active-slot selector. Do not use active-payload rollback as normal recovery.
- [x] Keep a pre-two-slot versioned `GameSave` loadable as the compatibility source until successful slot adoption; clear the compatibility source, both slots, and selector when a run is rejected, completed, or cleared.
- [x] Route menu save discovery and gameplay loading through the active-save API. Report either **Save failed. Your previous save is still available.** or **Save failed. No save was created.**
- [x] Add deterministic regressions for first save, legacy adoption, alternating successes, candidate verification/write failure, selector-promotion failure, unchanged prior payload, prior-save loadability, and clearing. Evidence: instrumentation source compiles in `assembleDebugAndroidTest`; device execution is pending.
- [ ] Update ADR 0007, persistence documentation, the plan, and the PR walkthrough so no rollback claim remains current. The repository documents are updated; the PR walkthrough remains pending.
- [x] Run the complete API 34+ connected suite. Evidence: `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passed with 32/32 unit tests, and `connectedDebugAndroidTest` passed 18/18 on `Medium_Phone_API_36` (`emulator-5554`, API 36) on 2026-10-05.
- [x] Capture a concise touch-visible failed-save/previous-load recording. Evidence: a six-second trimmed API 36 recording shows **Save failed. Your previous save is still available**, the menu transition, the Load Game input, and restoration of score `111`; PR upload is pending.
- [ ] **Two-slot ready gate:** automated and device evidence demonstrates that every failed attempt leaves the former active payload unchanged and loadable, successful saves alternate/promote only after verification, legacy adoption works, all stores clear together, and the PR documentation/evidence is current.

## 0F — integration evidence and delivery

**Dependencies:** 0A–0E. **Files:** meaningful tests under `android/app/src/test/` and `android/app/src/androidTest/`; documentation and PR template only unless verification exposes an in-scope defect.

1. [ ] Replace the arithmetic starter with regression coverage for exact recipe matching (duplicate potatoes included), wrong/extra ingredients, order expiry, completion points/streak reset, and three-failure finalization. Add deterministic clock/sync seams only where needed; retain the current documented balance. Correct resource/package smoke checks where useful.
2. [ ] Run `gradlew.bat assembleDebug` and `gradlew.bat testDebugUnitTest`. With an attached API 34+ emulator/device, run `gradlew.bat connectedDebugAndroidTest`. Record command, result, date, device/API for device checks, and the behavior each test protects. A skipped check is not a pass.
3. [ ] Complete the smoke matrix below offline/signed out. Repeat lifecycle entry/exit and surface recreation checks at least five times. Test game-over save/stat behavior both from a new session and from a loaded one.
4. [ ] Update the owning documentation with actual final behavior, commands, limitations, and accepted decisions. Mark individual report findings resolved only with a source/test reference and date; keep deferred findings visible. Do not rewrite the report as if the original findings never existed.
5. [ ] Review all Luna changes against this contract, confirm no accidental configuration/asset exposure, and finalize the Phase 0 integration/closeout PR using `.github/pull_request_template.md`. Include changelog, reason, material trade-offs, relevant decisions, deferred risks, command/test results, and device evidence. Attach labelled screenshots for every materially changed scene, using before/after views when useful; attach a short recording or concise screenshot sequence for interaction or lifecycle behavior that a still image cannot prove. Record the device/emulator and API level, note any unverified scenario, and redact sensitive data. Commit messages follow Conventional Commits. Do not merge automatically.

- [ ] **0F gate:** evidence supports every Phase 0 criterion and the integration/closeout PR satisfies its ready-for-review gate. Without device evidence, status is implementation complete/device verification pending, not ready for review. Phase completion remains separate: every delivery-map PR must be approved and merged by the owner, followed by owner approval of Phase 0 closeout.

## Manual acceptance checklist

- [ ] **Fresh launch signed out, airplane mode:** menu and game open; saved joystick/volume work; no crash or network gate.
- [ ] **Valid dish, duplicate-ingredient recipe, invalid dish:** correct dish/Waste results; matching submission consumes once and awards the current rule's points.
- [ ] **Basket, table, rubbish, collision:** existing interactions and tile movement work; no stuck swap blocker after a normal fetch.
- [ ] **Three order failures:** exactly one game-over/stat update; high score remains local; no further order spawning.
- [ ] **Pause during cook/fetch; wait 10 seconds:** gameplay work freezes after transition, resumes once, and preserves remaining progress.
- [ ] **Pause menu → background → foreground:** pause/menu state persists; Resume is explicit.
- [ ] **Running game → background → foreground:** background time does not expire orders or complete cooking/fetching.
- [ ] **Two simultaneous direction touches; release; exit:** movement stops and no old input runnable continues.
- [ ] **Save DONE pot twice; resume/collect:** saving does not remove food; collect yields exactly one item.
- [ ] **Load COOKING pot → save again → reload:** no null recipe/progress; cooking resumes and completes once.
- [ ] **Load malformed or completed legacy save:** visible recovery, no partial state/stat award; existing stored data is not silently deleted.
- [ ] **Complete a loaded game → Load again:** saved run is cleared; no replay from the completed save.
- [ ] **Enter/skip/complete tutorial; repeat five times:** one session, correct tutorial pause/controls, no hidden game-over callbacks.
- [ ] **Destroy/recreate while workers run or wait:** workers/callbacks close safely, one new render loop, no UI teardown hang.

## Dependencies and owner involvement

- The orchestrator can investigate/fix the build, delegate bounded Luna work, write tests, review changes, and prepare a PR after this plan is approved and execution is requested.
- Owner review of the detailed scope remains required; ADR 0003 was accepted on 2026-10-04. No live Firebase access or backend changes are required for this phase's guest/offline gates.
- A compatible device/emulator is required for completion evidence. The agent owns the environment preflight/setup above and can launch a configured AVD without Android Studio. Ask the owner only for an actual missing prerequisite the agent cannot resolve or device-only observations needing their participation.
- If the chosen fix would reset valid saves, replace the save schema, alter cloud conflict behavior, change gameplay balance, or expand platforms, stop that dependent work and present a revised concrete proposal. Do not infer approval from this plan.

## Ready-to-use Luna slice handoff

> Implement Phase 0 slice **[0A–0F]** from the approved `plans/phase-0-baseline-recovery.md` contract. Read root `AGENTS.md` and the slice's owning docs. Predecessor evidence: **[results]**. Approved decisions: **[ADR status]**. Stay within the listed files/responsibilities; preserve unrelated changes and Firebase configuration. Implement the slice's acceptance behavior with focused regression tests and its documentation updates. Run its available checks from `android/`. Return changed files, rationale, actual test/manual results, and remaining risks; identify missing device/backend evidence honestly. Do not implement later phases, merge a PR, or change unapproved product/persistence decisions. The orchestrator will inspect and verify this slice before assigning the next.
