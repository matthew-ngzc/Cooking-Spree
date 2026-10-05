# Cooking Spree — agent guide

## Read just enough

1. Start with [docs/README.md](docs/README.md). It routes each kind of task to a small, purpose-built document.
2. For an implementation task, read the relevant document(s), then the named source files. Treat source and Gradle configuration as authoritative when they disagree with prose.
3. Do not recursively load assets, generated Gradle files, or the historical logs unless the task requires them.

## Delivery loop

For implementation work, Terra and Sol are orchestrators: make a detailed, reviewable plan before coding. When the user has asked to be grilled or a plan already resulted from that discussion, use that agreed plan as the implementation contract. Hand the bounded implementation task, acceptance criteria, and affected files to a Luna agent. The orchestrator reviews the result, integrates it, runs the relevant checks, and reports evidence.

All maintained execution plans use GitHub-flavoured Markdown task checkboxes (`- [ ]` / `- [x]`) for actionable work, verification, gates, and owner approvals so progress can be updated in place. Add checkboxes when creating or refining a plan and update them as work is completed. Check an item only after its acceptance evidence exists; include or link that evidence near the item when practical. Checkboxes record progress but do not authorize unapproved work or promote inbox ideas. Only the product owner may check an owner-approval item.

Before implementation, divide the plan into a **PR delivery map**. Prefer small, independently reviewable PRs rather than one phase-sized PR. For each PR, state its assigned slices, intended base and dependencies, whether it can merge independently, its verification/evidence requirements, and the exact gate for changing it from draft to ready for review. A PR is ready when every slice assigned to that PR is complete, its documentation and walkthroughs are current, and its required automated, manual, and visual evidence is present or explicitly unavailable; later slices elsewhere in the plan do not keep it in draft.

After making one PR ready for review, continue with the next planned PR without waiting for owner review or merge unless the plan marks a real blocker. Real blockers include a required owner/product decision, an unavailable external prerequisite, a predecessor review outcome that could materially change the next scope, or work that cannot be isolated safely from an unmerged dependency. Start an independent next PR from its normal target branch. When the next PR depends on an unmerged predecessor, create a stacked branch from the predecessor and target the new PR at the predecessor branch so its diff contains only the new work. After the predecessor merges, retarget or rebase the dependent PR as appropriate. Keep prerequisite branches available until dependent PRs have been restacked. This continuation rule never authorizes the agent to merge a PR.

For a small direct fix where delegation would cost more than it saves, state that judgment and proceed. Do not delegate a task whose safety or product decision still needs the user's answer.

## Product and platform direction

Cooking Spree serves casual gamers: it must remain enjoyable offline as a single-player game, with optional online leaderboard competition. The intended future social mode is in-person multiplayer for gatherings, in the spirit of a party cooking game. Android is the only active platform. Start iPhone work only after the user has established and accepted an Android-completion roadmap; do not let prospective iPhone support expand current Android tasks. Read [docs/product.md](docs/product.md) for product decisions.

Read [docs/direction.md](docs/direction.md) before proposing product work and [plans/android-roadmap.md](plans/android-roadmap.md) before planning execution. Preserve every uncommitted inspiration in [docs/ideas/inbox.md](docs/ideas/inbox.md); agents may connect or refine an idea but only the user can promote it into committed roadmap work.

## Change, commit, and PR contract

Agents may commit and open pull requests. Use [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) for commit messages. Split implementation into small commits, each representing one coherent purpose. Open each PR as a draft when its first reviewable commit is available, keep it current throughout its assigned slices, and convert it to ready for review when its own ready gate is satisfied. The user reviews every PR and has final approval; opening or finalizing a PR never authorizes a merge.

After each commit, update the draft PR through `gh` with a commit walkthrough. Group files that contribute to the same behavior and explain them together rather than repeating the diff file by file. For every commit, record:

1. The commit SHA/title and the behavior it changes.
2. Why the change is needed and its significance to the player or system.
3. A concise scenario, sequence, or Mermaid diagram when it materially clarifies the behavior.
4. The changed files with precise GitHub diff links to representative lines.
5. Verification results and the strongest available review evidence.

A PR must also contain a clear overall changelog, material decisions/trade-offs, associated documentation updates, and any deferred or unverified scenario.

Every PR, in every phase and for work outside the roadmap, must include evidence that makes the changed behavior reviewable without checking out the branch. Attach useful visual evidence whenever the implementation or fix has a visible or device-observable result. For visual or scene changes, attach labelled screenshots of every materially changed scene and include before/after views when they clarify the difference. For interaction, animation, or lifecycle behavior, prefer a short screen recording with visible touch/click indicators so the reviewer can follow both inputs and transitions; use a concise screenshot sequence only when recording is unavailable or a still comparison is materially clearer, and explain that choice. Use `gh pr edit --attach` or `gh pr comment --attach` to upload images and videos as the draft evolves. Non-visual changes still require relevant command/test results; explain why visual evidence is not useful when omitted. State the device or emulator and API level used for Android evidence, identify any scenario that was not verified, and redact account details, tokens, Firebase configuration, and other sensitive data from all evidence.

Treat reviewer-facing recordings as edited evidence, not raw automation logs. Stage the app and fixture before recording, start immediately before the first meaningful input, and drive the complete flow through one local script or continuous controller with condition-based waits where practical. Agent deliberation, tool-call latency, approval waits, and oversized safety sleeps are not part of the evidence. If the capture method includes them, trim the recording locally or rerecord it before upload. Split independent scenarios into separate focused clips. Capture internal app/system audio whenever the recorder supports it, especially when sound changed or helps establish the flow; keep microphone input disabled unless narration or ambient sound is itself the evidence. Audio does not justify extra idle time or a longer clip. Review every final file from beginning to end: the first meaningful input must occur within two seconds, no unexplained idle gap may exceed three seconds, and the final result must remain visible for one to two seconds. A naturally slow app operation may exceed those bounds when the delay itself matters; label it in the PR comment. Upload only after playback confirms visible inputs, legible transitions, correct orientation, useful pacing, synchronized unclipped audio, no private/background speech, and no sensitive data. If internal audio is unavailable, say so in the PR evidence instead of substituting microphone noise.

Use `.github/pull_request_template.md` when opening a PR.

For a decision that changes architecture, persistence/data compatibility, platform scope, multiplayer/online approach, or a product commitment, create a concise ADR under `docs/decisions/` before or alongside implementation. The user decides whether it is accepted. See [docs/decisions/README.md](docs/decisions/README.md).

## Documentation is part of the change

Every behavior, architecture, build, test, persistence, map, asset, or workflow change has a documentation owner: the agent making the change. Follow the deterministic owner lookup, prose/diagram update, and completion process in [docs/doc-maintenance.md](docs/doc-maintenance.md); route contributors through [docs/README.md](docs/README.md). Keep diagrams beside their owning contract and remove superseded claims.

## Project boundaries

- This is a native Android Java game in `android/`; run Gradle commands from that directory.
- Preserve `android/app/google-services.json`; it is Firebase configuration. Do not reproduce its contents in documentation, logs, or commits.
- `Tiled stuff/` is map-authoring source; `android/app/src/main/assets/map.tmj` is the runtime map. Keep them deliberately synchronised when map work is requested.
- `new sprites/` is source artwork; runtime map assets are in `android/app/src/main/assets/tiles/`, while Android UI artwork is in `android/app/src/main/res/drawable/`.
- This project is one Git repository rooted here. Run Git commands from this project root.
- Canonical project guidance and documentation live at this root under `AGENTS.md`, `docs/`, and `plans/`.

## Verification baseline

Use `gradlew.bat testDebugUnitTest` for local unit tests and `gradlew.bat connectedDebugAndroidTest` only with an attached emulator/device. Follow [docs/development.md](docs/development.md) for setup, build, manual smoke tests, and current limitations.
