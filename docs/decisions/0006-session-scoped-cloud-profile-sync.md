# ADR 0006: Session-scoped cloud profile sync

Status: Accepted
Date: 2026-10-03
Amended: 2026-10-04

## Context

Cooking Spree must preserve a complete device profile when the network or cloud is unavailable, while giving an authenticated player explicit control when that profile differs from the cloud profile attached to their Google account. Repeated conflict prompts during play would interrupt a casual game, but silently preferring either copy could discard progress. Per-field cloud writes would also turn one completed game into several independent failures and alerts.

## Decision

At a cold app-session start, load and display the device profile first. If a Google account is already authenticated, attempt one cloud-profile read for that session. A failed or unavailable read leaves the app in **device-only** mode without blocking play. Do not ask again while that app process remains active.

When the cloud read succeeds, compare the complete device and cloud profiles. If they are identical, enable **cloud-sync** mode without prompting. If they differ, show both choices with concise identifying facts: chef name, high score, games played, last-updated time, and account level only if a level system has been separately defined and implemented. Offer three explicit actions:

- **Use this device**: choose the device profile, write it to the cloud, and enable cloud-sync mode.
- **Use cloud**: choose the cloud profile, replace the device profile, and enable cloud-sync mode.
- **Not now**: keep the device profile and use device-only mode for the remainder of the session.

The choice applies for the rest of that active app session and is not prompted again until a later cold start. A player who signs in after startup becomes eligible for comparison on the next cold start; sign-in itself does not interrupt the current session with a conflict prompt.

Provide a player-initiated **Sync with cloud** action in the signed-in account/profile area outside active gameplay. This action is available whether the current session is device-only or cloud-sync. It is an explicit exception to the automatic once-per-cold-start comparison: because the player requested it, the app may read and compare the profiles again during the same session.

Manual sync always begins by reading the complete cloud profile while continuing to show the device profile. If the read fails or the device is offline, preserve the current device data and session mode and show a cloud-unavailable message. If the profiles match, report that the profile is up to date and enable or retain cloud-sync mode. If they differ, show the same identifying facts and **Use this device**, **Use cloud**, or **Not now** choices; never infer that a previously failed cloud write makes either copy authoritative. Choosing **Not now** preserves the current device profile and device-only mode. A failed upload after **Use this device** follows the same local-success/cloud-failure alert behavior as any other logical cloud save.

In device-only mode, profile and aggregate-stat updates write only to device storage. In cloud-sync mode, each logical update writes to device storage first and then attempts one atomic cloud-profile update. If that cloud write fails, retain the successful device update and show one alert for that logical save attempt: cloud saving failed and the data was saved on this device only. The alert offers **Continue** and **Report issue**. Continuing does not discard local progress or disable later cloud attempts; subsequent logical updates may try the cloud again. The reporting route and redacted diagnostics must be specified during Phase 1 planning and must not expose tokens, backend configuration, account identifiers, or profile contents.

All player-facing labels, prompts, and errors use **cloud** terminology. They must not name Firestore, Firebase, a database, or another storage provider; those names are implementation details confined to engineering documentation and diagnostics.

Each account-linked device profile must be scoped to the authenticated account so profiles from different users on the same device are never compared or overwritten as if they belonged to one player. Profile snapshots require a last-updated value and a complete, documented schema; active in-progress games remain device-local.

## Consequences

Players can begin offline immediately, make one informed automatic conflict decision per launch, and retain local progress through read or write failures. They can deliberately revisit that decision or recover after offline cloud writes through manual sync without waiting for another cold start. Cloud-sync mode is an explicit session state rather than an incidental consequence of authentication. Game completion and other multi-field changes need a single snapshot/batch write so one player action produces at most one failure alert. The current field-by-field synchronization and unconditional cloud-to-local hydration do not satisfy this decision and must be replaced in Phase 1.
