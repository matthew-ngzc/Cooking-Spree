# ADR 0006: Session-scoped cloud profile sync

Status: Accepted
Date: 2026-10-03

## Context

Cooking Spree must preserve a complete device profile when the network or Firestore is unavailable, while giving an authenticated player explicit control when that profile differs from the profile attached to their Google account. Repeated conflict prompts during play would interrupt a casual game, but silently preferring either copy could discard progress. Per-field cloud writes would also turn one completed game into several independent failures and alerts.

## Decision

At a cold app-session start, load and display the device profile first. If a Google account is already authenticated, attempt one Firestore profile read for that session. A failed or unavailable read leaves the app in **device-only** mode without blocking play. Do not ask again while that app process remains active.

When the Firestore read succeeds, compare the complete device and cloud profiles. If they are identical, enable **cloud-sync** mode without prompting. If they differ, show both choices with concise identifying facts: chef name, high score, games played, last-updated time, and account level only if a level system has been separately defined and implemented. Offer three explicit actions:

- **Use this device**: choose the device profile, write it to Firestore, and enable cloud-sync mode.
- **Use cloud**: choose the cloud profile, replace the device profile, and enable cloud-sync mode.
- **Not now**: keep the device profile and use device-only mode for the remainder of the session.

The choice applies for the rest of that active app session and is not prompted again until a later cold start. A player who signs in after startup becomes eligible for comparison on the next cold start; sign-in itself does not interrupt the current session with a conflict prompt.

In device-only mode, profile and aggregate-stat updates write only to device storage. In cloud-sync mode, each logical update writes to device storage first and then attempts one atomic Firestore profile update. If that cloud write fails, retain the successful device update and show one alert for that logical save attempt: cloud saving failed and the data was saved on this device only. The alert offers **Continue** and **Report issue**. Continuing does not discard local progress or disable later cloud attempts; subsequent logical updates may try Firestore again. The reporting route and redacted diagnostics must be specified during Phase 1 planning and must not expose tokens, Firebase configuration, account identifiers, or profile contents.

Each account-linked device profile must be scoped to the authenticated account so profiles from different users on the same device are never compared or overwritten as if they belonged to one player. Profile snapshots require a last-updated value and a complete, documented schema; active in-progress games remain device-local.

## Consequences

Players can begin offline immediately, make one informed conflict decision per launch, and retain local progress through read or write failures. Cloud-sync mode is an explicit session state rather than an incidental consequence of Firebase authentication. Game completion and other multi-field changes need a single snapshot/batch write so one player action produces at most one failure alert. The current field-by-field synchronization and unconditional cloud-to-local hydration do not satisfy this decision and must be replaced in Phase 1.

