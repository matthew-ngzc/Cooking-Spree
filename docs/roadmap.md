# Known direction and limitations

Purpose: preserve historical planning context, not an automatic implementation queue. Source: `plans/COOKING SPREE future plans.md`. The accepted product priorities are in [direction.md](direction.md), and the living execution roadmap is [../plans/android-roadmap.md](../plans/android-roadmap.md). iPhone work begins only after the product owner accepts Android-roadmap completion.

## Remaining unfinished work

- Replace the unfinished field-by-field profile synchronization with the explicit device/cloud conflict model while preserving signed-out, offline play and device-local saved runs.
- Validate/fix profile and aggregate-stat migration between preferences and Firebase. Joystick persistence and local-first failure behavior were completed in Epic 0.
- Build a leaderboard, friends/following, and an improved action-gated tutorial.
- Make the map/UI robust across device dimensions.
- Rebalance difficulty, then consider skins, power-ups, currency, multiplayer, and store ideas. The multiplayer vision is a co-located party mode, not a commitment to remote online multiplayer.
- Audit asset/music licensing and visual consistency before distribution; publishing is not complete.

## Current technical caveats

- Firebase backend rules, schema behavior, and live profile synchronization remain unverified; Epic 1 owns that work.
- Epic 0 established 36 unit tests and a 19-scenario API 36 connected suite; current commands and evidence are in [development.md](development.md).
- Layout/map dimensions are deliberately fixed in several code paths.
- `SimpleTutorialActivity` is unused; decide whether to remove or revive it before investing in a second tutorial path.
- Old files and comments use “process” for what the UI now calls an order. Preserve behavioral understanding while naming new work consistently.

For implementation sequencing and agent delegation, use [AGENTS.md](../AGENTS.md). For exact game behavior, use [gameplay.md](gameplay.md).
