# ADR 0007: Versioned and verified saved games

Status: Accepted
Date: 2026-10-05
Accepted: 2026-10-05
Amended: 2026-10-05 — the owner selected two-slot promotion instead of overwriting and rolling back the active payload.

## Context

The Phase 0 legacy loader could validate individual fields yet still tell a player that a previously successful save could not be loaded and silently continue as a fresh game. The product owner requires `Game saved` to identify a verified recovery point, permits deliberate invalidation only across incompatible game releases, and does not consider cosmetic releases such as skins incompatible.

## Decision

Use three-part semantic game versions, beginning with `1.0.0`. Every save stores the complete producing version. The semantic major number is the save-compatibility boundary: equal majors are compatible, while missing, malformed, older-major, and newer-major values are incompatible. Increment the major version for incompatible map, save-schema, catalogue-identity, or saved-rule changes; do not increment it solely for cosmetic skins, sprites, audio, text, or compatible fixes/features.

Capture paused gameplay into a canonical snapshot using the player's last fully reached traversable tile, stable persisted identities, and valid explicit `Waste` state independent of active orders. Validate before writing. Keep two physical save slots and a small active-slot selector. Write a candidate only to the inactive slot, synchronously read it back, and fully validate it before promoting the selector. Never rewrite the active save payload as part of a save attempt. A capture, validation, candidate-write, readback, or selector-promotion failure leaves the former slot active, reports failure immediately, and tells the player that the previous save remains available. A failed first save instead says that no save was created.

Treat the existing versioned `GameSave` preference file as a compatibility source when no two-slot selector exists. It remains loadable and untouched through a failed first promotion. A successful promotion adopts a real slot; after a later successful promotion proves both real slots participate, the compatibility source may be cleared. Rejected, completed, or explicitly cleared runs clear the compatibility source, both slots, and the selector.

Loading constructs and validates the complete session while gameplay workers remain blocked, then publishes it as one result. An incompatible save shows an error containing its stored full version and the current full version; a same-major malformed or inconsistent save shows a corruption error. After the player acknowledges either error, clear the rejected save and return to the menu without starting or exposing a fresh or partially restored session. A terminal compatible save restores/finalizes its result exactly once.

## Consequences

Previously unversioned saves become intentionally incompatible and are cleared after an explanatory acknowledgment. Releases must consciously classify compatibility and bump the major version before shipping an incompatible state change. Two slots use slightly more local storage and require all save discovery, loading, and clearing to go through `PrefsHelper`; direct reads of one preference file are invalid. Save/load tests must cover version comparison, stored/current version copy, inactive-slot readback verification, failed selector promotion, unchanged prior-slot payloads, legacy adoption, alternating successful saves, committed-tile capture, corruption clearing, no-fresh-session failure, terminal restoration, off-order dishes, and `Waste`. This decision supersedes ADR 0003 only where ADR 0003 retained an unversioned legacy format and preserved invalid loads.
