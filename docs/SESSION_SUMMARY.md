# MaaEnd Android Session Summary

Date: 2026-04-19  
Workspace: `/workspace/MaaEnd-Android`  
Upstream assets repo: `/workspace/MaaEnd`  
Goal of this session: make Android build, root runtime, preview window, task configuration, and MaaFramework task execution usable without re-hitting the same packaging/runtime traps.

## 1. Read This First Next Session

The highest-value lessons from this session are:

- Always build the Android app with JDK 21.
- `../MaaEnd/assets` must exist, or task/catalog UI will look empty.
- `runtime/agent/go-service` must be an Android executable, not a Linux ELF.
- Android packaging silently dropped `__Private` pipeline directories.
- `AndroidOpenGame` should use the upstream resource task, not a custom Kotlin preflight flow.
- If logs look contradictory, check timestamps first. Old `root-runtime.log` and `go-service.log` entries remain and can mislead.

## 2. End-of-Session State

By the end of this session, these parts were working or intentionally stabilized:

- APK builds successfully.
- Root permission and root runtime connection work.
- Runtime assets auto-prepare under `/data/local/tmp/com.maaend.android/maaend-runtime/v1`.
- In-app preview window works and stays on a fixed `1280x720` virtual display.
- Preview interaction uses real `MotionEvent` injection on the target `displayId`.
- Task page supports:
  - compact left-side task list
  - per-task configuration on the right
  - multi-select execution in top-to-bottom order
  - persisted task config values
- `AndroidOpenGame` was restored to the upstream MaaEnd resource task flow.
- Internal framework diagnostics were expanded so task, node, recognition, action, and controller events can be inspected from Android logs.

Most important final runtime fact:

- The recurring `__ScenePrivateWorldEnterMapAny` failure was traced to `AutoAltClickAction`.
- The real reason was not recognition failure.
- The real reason was that Android packaging dropped the `__Private` resource overlays needed to turn PC-only `AltKeyDown/AltKeyUp` into Android-safe `DoNothing`.

## 3. Biggest Pitfalls and How To Avoid Them

### Pitfall 1: wrong JDK breaks Gradle/Kotlin

Symptom:

- Gradle or Kotlin compilation failed under a newer system Java.

Rule:

- Use Homebrew `openjdk@21` for this repo unless Gradle/AGP is deliberately upgraded.

Build command:

```bash
JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
sh gradlew :app:assembleDebug
```

### Pitfall 2: empty task list is usually missing upstream assets

Symptom:

- App launched, but task list was empty or catalog looked broken.

Root cause:

- The Android app packages `../MaaEnd/assets`.

Rule:

- If task UI is empty, first check that `/workspace/MaaEnd/assets` exists before debugging parser/UI logic.

### Pitfall 3: runtime missing was a packaging problem, not a task problem

Symptom:

- `maa runtime missing`

Root cause:

- `runtime/agent/go-service` or `runtime/maafw` was absent or incomplete.

Rule:

- Before touching task logic, verify:
  - `runtime/agent/go-service`
  - `runtime/maafw/libMaaFramework.so`
  - runtime extraction under `/data/local/tmp/.../maaend-runtime/v1`

### Pitfall 4: `AndroidOpenGame` regressed when custom Kotlin flow replaced resource flow

Symptom:

- `AndroidOpenGame` started failing with custom `preflight OpenGame failed ...`
- later tasks never started

Root cause:

- A custom Kotlin `driveOpenGameFlow(...)` preflight became more fragile than the upstream resource task.

Rule:

- Prefer the upstream resource task `AndroidOpenGame -> OpenGame` unless there is a very specific Android-only blocker.
- If custom preflight is added for debugging, remove it once the original task can run.

### Pitfall 5: stale root runtime processes create stale virtual displays

Symptom:

- Many stale `MAAEND_VD` displays accumulated.
- Game and controller could target different displays.

Rule:

- Before debugging recognition failure, verify:
  - current virtual display id
  - actual game activity display id
  - stale `root_runtime` processes

### Pitfall 6: fullscreen preview cannot be a translucent dialog

Symptom:

- Expanded preview looked like a grey overlay with underlying content visible.

Root cause:

- `SurfaceView` + translucent dialog layering is unreliable.

Rule:

- Use an in-activity fullscreen overlay, not a dialog.

### Pitfall 7: preview lifecycle and display lifecycle must stay separate

Symptom:

- Switching tabs caused black preview.

Root cause:

- Preview surface loss accidentally stopped the virtual display.

Rule:

- Losing the UI surface should only detach the monitor surface.
- Explicit stop paths should be the only place that destroys the virtual display.

### Pitfall 8: task config must come from MaaEnd task JSON

Symptom:

- Tasks such as `VisitFriends` failed because required options were never exposed in Android UI.

Rule:

- Before patching runtime behavior, inspect task JSON:
  - `option`
  - `default_case`
  - `pipeline_override`

### Pitfall 9: execution selection and config focus are different concepts

Rule:

- Card click means “currently editing this task”.
- Checkbox means “include this task in execution order”.
- Do not collapse those states into one.

### Pitfall 10: `go-service` can silently become the wrong binary format

Symptom:

- Runtime logs showed:

```text
agent setup failed: Cannot run program ".../agent/go-service": error=2
```

Real cause:

- The binary existed, but it had been rebuilt as Linux ARM64 ELF with interpreter:

```text
/lib/ld-linux-aarch64.so.1
```

- Android requires:

```text
/system/bin/linker64
```

Rule:

- Never rebuild `runtime/agent/go-service` with `GOOS=linux`.
- After rebuilding, always run:

```bash
file runtime/agent/go-service
```

- The output must mention:

```text
interpreter /system/bin/linker64
```

### Pitfall 11: Android packaging dropped `__Private` directories completely

This was the single most important packaging discovery of the session.

Observed facts:

- Upstream contains:
  - `assets/resource/pipeline/Common/__Private/AutoAltClick/Action.json`
  - `assets/resource_adb/pipeline/Common/__Private/AutoAltClick/Action.json`
- APK did not contain them.
- Device runtime did not contain them.

Effect:

- `resource_adb` never overrode the private PC-only `AutoAltClick` helper actions.
- Framework still ran the PC variant:
  - `__AutoAltClickAltKeyDownAction`
  - `__AutoAltClickAltKeyUpAction`

Fix applied:

- These files are now staged into a safe path during packaging:

```text
assets/bundled_runtime/private_pipeline/...
```

- During `prepareRuntime()`, they are restored back into:

```text
resource/pipeline/Common/__Private/...
resource_adb/pipeline/Common/__Private/...
```

Important warning:

- The first implementation of this restore step pointed at `bundled_runtime/private_pipeline` inside runtime, but the extracted directory actually lived at `/data/local/tmp/.../private_pipeline`.
- That path bug was fixed.

### Pitfall 12: `AutoAltClickAction` was swallowing internal failures

Original problem:

- `AutoAltClickAction` ran three internal actions and always returned `true`, even if they failed.

Effect:

- Framework showed outer `Node.Action.Succeeded` while no real input was injected.

Fix applied:

- `AutoAltClickAction` now logs and returns failure if any internal `RunAction(...)` fails.

Why this mattered:

- It turned a vague symptom into a precise one:

```text
RunAction failed
action=__AutoAltClickAltKeyDownAction
node=__ScenePrivateWorldEnterMapAny
```

## 4. Key Root Causes Proven During This Session

These were not guesses by the end; they were proven with logs or file inspection.

### 4.1 `TaskIcon` recognition was not the blocker

Observed:

- `__ScenePrivateWorldEnterMapAny` recognition succeeded.
- Match score was around `0.94+`.

Meaning:

- The map entry icon was correctly recognized.
- Failure was after recognition, not before.

### 4.2 `MapMind` / map-success recognition was a later blocker

Observed before the `AutoAltClick` failure was fully exposed:

- `__ScenePrivateAnyEnterMapSuccess` repeatedly failed with template score around `0.52`.

Meaning:

- Even when the map-open path progressed, “map successfully opened” recognition was weak on Android.

### 4.3 The true action-level blocker was `AutoAltClick`

Final high-signal sequence:

- `__ScenePrivateWorldEnterMapAny` recognition succeeded
- `Node.Action.Starting`
- `Node.Action.Failed`
- `go-service` logged failure on `__AutoAltClickAltKeyDownAction`

Meaning:

- The node was not failing because it could not be found.
- It was failing because PC-only private action helpers were still in play.

## 5. Important Files Changed in This Session

Build and packaging:

- `app/build.gradle.kts`
- `app/src/main/java/com/maaend/android/runtime/RuntimeBootstrapper.kt`

Framework bridge and execution diagnostics:

- `app/src/main/java/com/maaend/android/maa/MaaFrameworkBridge.java`
- `app/src/main/java/com/maaend/android/root/RootRuntimeService.kt`
- `app/src/main/java/com/maaend/android/bridge/DriverClass.kt`
- `app/src/main/java/com/maaend/android/bridge/InputControlUtils.java`
- `app/src/main/native/bridge.cpp`

Preview / virtual display:

- `app/src/main/java/com/maaend/android/preview/VirtualDisplayManager.kt`
- `app/src/main/java/com/maaend/android/preview/ActivityUtils.kt`
- `app/src/main/java/com/maaend/android/ui/MaaEndApp.kt`

Task config / persistence:

- `app/src/main/java/com/maaend/android/catalog/InterfaceCatalogLoader.kt`
- `app/src/main/java/com/maaend/android/ui/MainViewModel.kt`
- `app/src/main/java/com/maaend/android/storage/AppSettingsRepository.kt`
- `app/src/main/java/com/maaend/android/model/AppModels.kt`

Upstream Go service changes used for debugging:

- `/workspace/MaaEnd/agent/go-service/common/autoaltclick/action.go`

## 6. Commands That Were Actually Useful

### Verify `go-service` binary format

```bash
file runtime/agent/go-service
```

Expected:

```text
interpreter /system/bin/linker64
```

### Correct Android `go-service` build command

Run inside `/workspace/MaaEnd/agent/go-service`:

```bash
CC="$HOME/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android30-clang" \
CXX="$HOME/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android30-clang++" \
GOOS=android GOARCH=arm64 CGO_ENABLED=1 \
go build -o /workspace/MaaEnd-Android/runtime/agent/go-service .
```

Reason:

- `purego` on Android needs cgo-backed `dlfcn_android.go`.
- `GOOS=android` alone with `CGO_ENABLED=0` is not enough here.

### Quick runtime executable sanity check on device

```bash
adb shell '/data/local/tmp/com.maaend.android/maaend-runtime/v1/agent/go-service >/dev/null 2>&1; echo exit:$?'
```

Interpretation:

- `126` means wrong executable/interpreter format
- non-`126` means the binary at least launches

### Verify `__Private` resources exist on device runtime

```bash
adb shell 'find /data/local/tmp/com.maaend.android/maaend-runtime/v1/resource_adb/pipeline/Common/__Private -maxdepth 3'
adb shell 'cat /data/local/tmp/com.maaend.android/maaend-runtime/v1/resource_adb/pipeline/Common/__Private/AutoAltClick/Action.json'
```

### High-signal Android logs

```bash
adb logcat -d -s MaaFrameworkBridge InputControlUtils MaaEndBridge
adb shell tail -n 200 /data/local/tmp/com.maaend.android/maaend-runtime/v1/logs/root-runtime.log
adb shell tail -n 200 /data/local/tmp/com.maaend.android/maaend-runtime/v1/logs/go-service.log
```

## 7. Log Interpretation Rules

These rules would have saved time earlier in the session:

- `root-runtime.log` is append-only across runs. Always compare timestamps.
- `go-service.log` is also append-only. Old fatal lines can remain after a fix.
- “file exists” is not enough for `go-service`; the ELF interpreter matters.
- `Node.Action.Succeeded` on a `Custom` action is not enough if the implementation swallows internal errors.
- If `InputControlUtils` shows no touch injection for a framework click, the failure may still be inside the custom action before the controller is reached.

## 8. Best Next-Step Checklist If The Same Area Breaks Again

1. Confirm `runtime/agent/go-service` is Android ELF with `/system/bin/linker64`.
2. Confirm APK contains:
   - `assets/bundled_runtime/private_pipeline/resource/CommonPrivate/AutoAltClick/Action.json`
   - `assets/bundled_runtime/private_pipeline/resource_adb/CommonPrivate/AutoAltClick/Action.json`
3. Run `prepareRuntime()`.
4. Confirm device runtime contains:
   - `resource/pipeline/Common/__Private/AutoAltClick/Action.json`
   - `resource_adb/pipeline/Common/__Private/AutoAltClick/Action.json`
5. Run only `AndroidOpenGame`.
6. Then run only `DijiangRewards`.
7. If it still fails, inspect:
   - `Node.Action.Failed name=__ScenePrivateWorldEnterMapAny`
   - `go-service.log` lines containing `AutoAltClick`
   - `InputControlUtils` and `MaaEndBridge` touch injection logs

## 9. One-Line Takeaway

The most expensive repeated mistake in this session was assuming `resource_adb` overrides were not being used because of logic order, when the real issue was much simpler: Android packaging dropped the entire `__Private` directory tree, so the private helper actions never made it into the APK at all.
