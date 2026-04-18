# MaaEnd Android Root-Only MVP Plan

Date: 2026-04-19
Status: Draft

## 1. Goal

Build a first Android-native MVP for `MaaEnd` that runs directly on rooted devices without relying on Shizuku.

This MVP is intended to validate three things:

1. `MaaEnd` can run on Android with a stable Root execution chain.
2. Existing `ADB`-oriented resources and task logic can be reused at acceptable cost.
3. A small set of menu-heavy daily tasks can reach internal testing quality before expanding to realtime gameplay features.

## 2. Product Positioning

This is not a full Android release comparable to a mature public app.

The MVP is an internal-testable version with the following priorities:

1. Stable Root startup and task execution.
2. Small but useful task coverage.
3. Sufficient logs and screenshots for debugging.
4. Minimal UI required to configure, run, stop, and inspect tasks.

## 3. Target Constraints

To keep the MVP small, the first version is intentionally limited to:

- Rooted devices only
- Foreground mode only
- `arm64` only
- Android 11+
- Simplified Chinese UI only
- CN resource only
- No background virtual display mode
- No Shizuku compatibility layer
- No automatic update pipeline
- No scheduler / automation

Recommended Root environment for MVP:

- Prefer `Magisk`
- Do not promise broad ROM compatibility in MVP

## 4. MVP Scope

### 4.1 MVP Must-Do Checklist

The following items define the minimum work required for the MVP.

#### App Shell

- [ ] Build the Android app shell with `Home`, `Task`, `Preset`, `Running`, `Settings`, and `Log` pages
- [ ] Persist basic local settings
- [ ] Show current resource version and runtime status

#### Root Runtime

- [ ] Detect Root availability on launch
- [ ] Complete Root permission / authorization flow
- [ ] Launch privileged runtime successfully
- [ ] Handle runtime disconnect, stop, and restart cases

#### Runtime Integration

- [ ] Bundle and load required MaaEnd resources
- [ ] Run MaaEnd task entry points from Android
- [ ] Confirm selected MVP tasks can run under the Android packaging model
- [ ] Avoid introducing non-essential platform branches during MVP

#### Task Execution

- [ ] Support start task / stop task / stop on failure
- [ ] Show current running task and stage
- [ ] Preserve basic task parameters and preset choices
- [ ] Ensure task stop does not leave stuck input state

#### Diagnostics

- [ ] Record runtime logs
- [ ] Capture failure screenshots
- [ ] Export a diagnostics bundle for debugging
- [ ] Surface actionable error messages to testers

#### First Preset and Task Set

- [ ] Provide at least one usable daily preset
- [ ] `AndroidOpenGame`
- [ ] `DailyRewards`
- [ ] `DijiangRewards`
- [ ] `CreditShopping`
- [ ] `VisitFriends`
- [ ] `SellProduct`
- [ ] `AutoEssence`

#### Delivery Gate

- [ ] Validate on 2 to 3 rooted reference devices
- [ ] Complete at least one useful daily preset end-to-end
- [ ] Freeze MVP scope before stretch items begin

### 4.2 Must-Have User Flows

The MVP must support the following complete flow:

1. Open app
2. Detect Root availability and request/verify permission
3. Initialize bundled resources
4. Select preset or task
5. Start task
6. Watch progress and logs
7. Stop task safely
8. Export logs when something fails

### 4.3 Must-Have Screens

- Home page
- Task selection page
- Preset entry page
- Running page
- Settings page
- Log / diagnostics page

### 4.4 Must-Have Platform Capabilities

- Root detection and permission state display
- Root service startup and teardown
- Agent process launch and reconnect handling
- Resource extraction / loading
- Basic task parameter passing
- Runtime status display
- Failure screenshot capture
- Log export bundle

### 4.5 First Task Set

These tasks should define MVP completion:

- `AndroidOpenGame`
- `DailyRewards`
- `DijiangRewards`
- `CreditShopping`
- `VisitFriends`
- `SellProduct`
- `AutoEssence`

Selection原则:

- Prefer menu-heavy tasks
- Prefer flows already close to `ADB` support
- Avoid broad realtime combat / navigation dependence in first release
- Allow limited inclusion of a pre-validated Android/ADB foreground task

## 5. Stretch Scope

These may be added only if core MVP lands early and is stable:

- `SeizeEntrustTask`
- `DeliveryJobs`
- `GearAssembly`
- `BakerEntry`
- `EnvironmentMonitoring`

Stretch items must not delay MVP freeze.

## 6. Explicitly Out of Scope

The following items are not part of MVP:

- Shizuku support
- Non-Root devices
- Background mode
- Virtual display / screen-off running
- Floating control panel / overlay window
- Scheduler / timed tasks
- Auto update
- In-app resource hot update
- Multi-language support
- Multi-client support
- Complex task editor
- Full task coverage
- `RealTimeTask`
- `AutoCollect`
- `AutoEcoFarm`
- `AutoSell`
- `ProtocolSpace`
- `GiftOperator`
- `PuzzleSolver`
- `ImportBluePrints`
- `ItemTransfer`

## 7. Technical Breakdown

### 7.1 Android App Shell

Deliver a minimal native Android app that provides:

- App navigation
- Preset/task selection
- Settings persistence
- Execution status presentation
- Log browsing and export

Suggested stack:

- Kotlin
- Jetpack Compose
- Single-process UI app plus privileged Root execution component

### 7.2 Root Execution Chain

The Root path is the first critical milestone.

Required capabilities:

- Detect whether Root is available
- Request / confirm Root shell access
- Launch privileged service or process
- Establish a stable IPC path between app UI and privileged side
- Handle disconnect, process death, and stop requests

MVP decision:

- Build only one Root path
- Do not maintain Root + Shizuku parity in this phase

### 7.3 MaaEnd Runtime Integration

Integrate the following existing runtime pieces into Android packaging:

- MaaFramework runtime artifacts
- `agent/go-service`
- `agent/cpp-algo` if required by selected task set
- Bundled `assets/resource`
- Bundled `assets/resource_adb`

Key question for implementation:

- Which selected MVP tasks can run with only Go service + resource adaptation
- Which ones still require parts of `cpp-algo`

MVP principle:

- Prefer tasks that avoid forcing early Android packaging of the full C++ stack

### 7.4 Resource Strategy

For MVP, resources should ship inside the APK or first-run extracted package.

Do:

- Bundle a fixed resource version
- Extract once on first launch
- Track local resource version

Do not do:

- Online resource update
- Dynamic hot reload from remote source

### 7.5 Task Compatibility Gap Analysis

Before implementation freeze, each MVP task should be tagged as one of:

- `Ready`: can run with current Android/ADB resource path
- `Minor Patch`: needs small ADB-specific resource override or parameter adjustment
- `Major Patch`: depends on unsupported realtime control or desktop-only assumptions

If a task is still `Major Patch` after week 3, replace it with a simpler task.

### 7.6 Diagnostics

MVP debugging support must include:

- Runtime logs
- Important state transitions
- Task start/stop timestamps
- Failure screenshots
- Exportable diagnostics bundle

Without diagnostics, internal testing will stall.

## 8. Milestones

### Milestone A: Root Startup Demo

Definition:

- App launches
- Root becomes available
- Privileged side can start
- Basic ping succeeds

Exit criteria:

- Works on at least 1 reference rooted device

### Milestone B: Resource + Task Launch Demo

Definition:

- Resources load correctly
- A simple task can be started and stopped
- Logs are visible

Exit criteria:

- `AndroidOpenGame` or an equivalent simple task runs end-to-end

### Milestone C: Daily Preset Demo

Definition:

- At least 3 target tasks run from one preset
- Failures produce useful logs and screenshots

Exit criteria:

- Internal testers can complete at least one useful daily routine

### Milestone D: MVP Freeze

Definition:

- Scope locked
- Main critical bugs fixed
- Internal testing package available

Exit criteria:

- Passes MVP acceptance checklist

## 9. Suggested 8-Week Schedule

### Week 1

- Confirm architecture and packaging approach
- Create Android app skeleton
- Complete Root detection and permission flow
- Establish privileged process/service launch path

### Week 2

- Integrate MaaEnd resource packaging
- Implement app settings and local storage
- Implement basic task list / preset list UI
- Finish runtime log plumbing

### Week 3

- Run first simple task on device
- Validate start / stop / crash recovery flow
- Perform task compatibility review for selected MVP tasks
- Drop or replace tasks with major Android blockers

### Week 4

- Adapt `AndroidOpenGame`
- Adapt `DailyRewards`
- Adapt `VisitFriends`
- Improve failure screenshot collection

### Week 5

- Adapt `DijiangRewards`
- Adapt `CreditShopping`
- Start preset integration
- Add task parameter persistence

### Week 6

- Adapt `SellProduct`
- Adapt `AutoEssence`
- Stabilize execution state transitions
- Improve Root disconnect / reconnect behavior
- Begin internal smoke testing on multiple devices

### Week 7

- Fix critical bugs found in smoke testing
- Improve log export package
- Clean up UI rough edges
- Decide whether any stretch item is safe to include

### Week 8

- Freeze MVP scope
- Run regression pass
- Prepare internal test package
- Write install / known issues / troubleshooting notes

## 10. Acceptance Criteria

MVP is considered done only if all items below are true:

1. Fresh install to first task run can be completed in under 5 minutes on a prepared rooted device.
2. Root permission problems show actionable user feedback.
3. The app can start and stop tasks safely without leaving stuck input state.
4. At least one preset completes successfully on 2 to 3 reference devices.
5. The seven MVP tasks are either functional or deliberately removed before freeze.
6. Failures produce logs and screenshots sufficient for triage.
7. No out-of-scope feature is blocking release of the internal build.

## 11. Risks

### High Risk

- Root service lifecycle differs across ROMs
- Some chosen tasks may still depend on desktop-only timing or control assumptions
- Packaging `MaaEnd` runtime pieces on Android may expose unexpected toolchain issues

### Medium Risk

- ADB-oriented resources may not match real-device UI behavior closely enough
- Device resolution / scaling differences may break recognition timing
- Log volume and screenshot handling may affect storage or performance

### Low Risk

- Simple settings and preset UI
- Bundled fixed-version resources

## 12. Mitigation Strategy

- Keep MVP Root-only and foreground-only
- Use a fixed device matrix instead of claiming broad compatibility
- Replace blocked tasks early instead of forcing them through
- Freeze feature scope by week 6
- Treat diagnostics as a required feature, not a nice-to-have

## 13. Reference Device Matrix

Prepare at least:

- 1 primary rooted arm64 device for daily development
- 1 secondary rooted device with a different ROM or vendor skin
- 1 fallback test device if available

Track for each device:

- Android version
- Root solution
- ROM / vendor
- Display resolution
- Known quirks

## 14. Open Questions

These should be answered in week 1 or week 2:

1. Which MVP tasks can avoid `cpp-algo` packaging entirely
2. Whether Root side should be implemented as a long-lived service or short-lived task worker
3. Where best to store extracted resources and logs on Android
4. Whether screenshot capture can rely only on current control path for selected tasks
5. Which two or three devices define MVP compatibility

## 15. Scope Guardrails

If any of the following requests appear during MVP, defer them unless they unblock core stability:

- Add Shizuku support
- Add background mode
- Add more than one stretch task
- Add multi-language UI
- Add update system
- Add complex task editor

The first success condition is not "feature completeness".

The first success condition is "a rooted user can reliably run a small useful daily preset on Android".
