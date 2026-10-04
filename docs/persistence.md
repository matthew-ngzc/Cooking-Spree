# Persistence and account integration

Purpose: safely modify saves, settings, authentication, or Firebase. For game rules, see [gameplay.md](gameplay.md).

## Local stores

| Store | Owner | Contents |
| --- | --- | --- |
| `chef_prefs` | `PrefsHelper` | volume, joystick scale, language, profile fields, and aggregate stats. |
| `GameSave` | `GameActivity` / `PrefsHelper` | player position, score/failures, held item, table items, active orders, and pot states/contents/progress. Basket selection, ingredient-fetch state, and scoring streak state are not saved. |
| `ProcessManagerPrefs` | `GameManager` | legacy local high score written on game over. |

The save format remains the existing manually keyed legacy format (it is not versioned). Saving is allowed only from the manual pause menu. `GameSaveSnapshot` captures a non-consuming in-memory snapshot, including a player's logical tile while movement is interpolating, and validates it before `PrefsHelper` replaces the old key set in one editor commit. A failed capture/validation/write does not consume pot food or replace the prior save.

Loading adds a session `LOAD` pause before order scheduling and ingredient basket filling can mutate gameplay. `GameSaveParser` reads the entire `SharedPreferences.getAll()` map into a candidate and validates field types, counts, item/ingredient IDs, recipes (including legitimate `Waste`), pot consistency/progress, order times, map counts, and a nearby traversable player tile before any candidate field is applied. Invalid or terminal saves remain stored for recovery while a fresh session starts; a completed loaded game clears the save through the existing game-over path.

The legacy format remains coupled to map ordering and current recipe/item naming. Adding/reordering objects, modifying IDs, or changing a recipe/dish representation needs a migration/default strategy and a save/load smoke test. Basket selection, ingredient-fetch state, and scoring streak state remain intentionally unsaved.

## Firebase / Google sign-in

`AccountManager` uses Android Credential Manager to obtain a Google ID token, authenticates with Firebase Auth, and reads/writes Firestore. Configuration lives in `android/app/google-services.json`; treat it as sensitive configuration and do not copy it into docs.

Expected Firestore document: `chefs/{uid}` with `profile`, `stats`, and `settings` nested maps. The code also sketches social/following functionality, but it is not a complete feature.

`BaseActivity.onCreate()` initializes `PrefsHelper` with the application context and an Activity-free cloud sync adapter. Credential prompts remain owned by the foreground `MainActivity`'s `AccountManager`; the static preference helper does not retain an Activity. Preference and aggregate-stat setters commit to `chef_prefs` first, then request an asynchronous cloud update only when a current Firebase user exists. Synchronous adapter failures and asynchronous Firestore task failures produce a bounded diagnostic containing only the exception type. Remote cache hydration suppresses those upload calls, so loading account values is not treated as a user edit. Firebase initialization or sync failure leaves the local guest path available. Cloud sync remains optional and is not presented as production-ready.

The initial joystick radio selection is hydrated from the local setting before its user-change listener is installed, in both the menu settings and game settings. Completing a game updates local high score, average score, and games played, then clears `GameSave`; the focused unit seam uses a fake local stats store and does not require Firebase.

## Accepted Phase 1 sync behavior

[ADR 0006](decisions/0006-session-scoped-cloud-profile-sync.md) defines the replacement for the current unfinished synchronization. At cold startup, the app will show device data immediately, attempt at most one authenticated cloud comparison, and establish either device-only or cloud-sync mode for the remainder of that process session. A differing cloud profile must not overwrite local data without an explicit **Use this device**, **Use cloud**, or **Not now** choice. A signed-in **Sync with cloud** action in the account/profile area may repeat the complete comparison when deliberately requested, including after **Not now** or offline write failures; divergence uses the same explicit source choices. In cloud-sync mode, a logical multi-field update writes locally first and attempts one atomic cloud update; failure retains local data and shows one Continue/Report issue alert. All player-facing copy must say **cloud**, never Firestore, Firebase, database, or another provider name. This is accepted Phase 1 scope and is not implemented by the current field-by-field adapter.

The comparison snapshot will include chef name, high score, games played, and last-updated time. Account level is not currently part of the game/profile model and may be displayed only after a separate level-system decision and implementation. Local account-linked profiles must be namespaced by authenticated account so multiple Google users on one device cannot overwrite one another's cache. Active in-progress games remain device-local.

## Change checklist

- Maintain a local-first outcome for settings/gameplay data unless the user explicitly changes the product decision.
- Keep optional Firebase writes asynchronous and signed-out safe; local writes must not depend on cloud task success.
- Test signed-out, no-network, first sign-in, returning sign-in, and failed Firebase requests when changing account code.
- Test device/cloud equality, all three conflict choices, one-automatic-prompt-per-cold-start behavior, manual sync from device-only and cloud-sync modes, manual offline/read/upload failure, account switching, atomic logical updates, retry after a failed cloud write, and one failure alert per logical update.
- Avoid logging identity tokens, UIDs, email addresses, or raw preference dumps.
- Document any schema change here, including migration and rollback behavior.
