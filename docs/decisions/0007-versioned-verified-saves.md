# ADR 0007: Versioned and verified saved games

Status: Accepted
Date: 2026-10-05
Accepted: 2026-10-05

## Context

The Phase 0 legacy loader could validate individual fields yet still tell a player that a previously successful save could not be loaded and silently continue as a fresh game. The product owner requires `Game saved` to identify a verified recovery point, permits deliberate invalidation only across incompatible game releases, and does not consider cosmetic releases such as skins incompatible.

## Decision

Use three-part semantic game versions, beginning with `1.0.0`. Every save stores the complete producing version. The semantic major number is the save-compatibility boundary: equal majors are compatible, while missing, malformed, older-major, and newer-major values are incompatible. Increment the major version for incompatible map, save-schema, catalogue-identity, or saved-rule changes; do not increment it solely for cosmetic skins, sprites, audio, text, or compatible fixes/features.

Capture paused gameplay into a canonical snapshot using the player's last fully reached traversable tile, stable persisted identities, and valid explicit `Waste` state independent of active orders. Validate before writing, synchronously persist, read back, and validate the stored representation before reporting success. A failed save preserves the previous verified save and reports failure immediately.

Loading constructs and validates the complete session while gameplay workers remain blocked, then publishes it as one result. An incompatible save shows an error containing its stored full version and the current full version; a same-major malformed or inconsistent save shows a corruption error. After the player acknowledges either error, clear the rejected save and return to the menu without starting or exposing a fresh or partially restored session. A terminal compatible save restores/finalizes its result exactly once.

## Consequences

Previously unversioned saves become intentionally incompatible and are cleared after an explanatory acknowledgment. Releases must consciously classify compatibility and bump the major version before shipping an incompatible state change. Save/load tests must cover version comparison, stored/current version copy, readback verification, committed-tile capture, corruption clearing, no-fresh-session failure, terminal restoration, off-order dishes, and `Waste`. This decision supersedes ADR 0003 only where ADR 0003 retained an unversioned legacy format and preserved invalid loads.
