# Claude Notes

This is the canonical agent instruction file for this repository. If another agent entry point, including `AGENTS.md` or `AGENT.md`, points here, follow this file first.

## Project Summary

`MaaEnd Android` is a Root-only Android host app for MaaEnd. It references `MaaFramework-Android` as a Git submodule, prepares the Android runtime, starts the privileged Root runtime, syncs MaaEnd resources, shows preview/logs, and executes MaaEnd tasks directly on-device.

Current product boundary:

- Android 11+
- `arm64-v8a`
- Root required
- Foreground mode first
- Simplified Chinese UI first
- Do not add Shizuku, non-Root, background scheduler, or unrelated auto-update work unless the user explicitly asks for it.

Product direction:

- Prioritize task capability, task chains, presets, and the generic `interface.json` catalog/form model.
- Keep card focus and checkbox run selection separate: row/card click edits the task, checkbox includes it in the run order.
- Prefer phased delivery over large one-shot rewrites.
- Keep the existing generic task/preset architecture unless there is a concrete blocker.

## Framework Submodule

This app uses the shared framework from GitHub:

```text
MaaFramework-Android -> git@github.com:jh-akt/MaaFramework-Android.git
```

Gradle mounts it as:

```kotlin
include(":framework")
project(":framework").projectDir = file("MaaFramework-Android/framework")
```

App-specific code lives in `app/`. Reusable framework code lives in `MaaFramework-Android/framework/`.

If a fix is reusable across MaaEnd and Maa-bbb, make it in the sibling framework repository first (`../MaaFramework-Android`), then update this repo's submodule pointer. Do not re-copy framework source into `app/src/main/java/com/maaend/android/`.

## Privacy And Git Hygiene

- Before committing or pushing, verify `git config user.name` and `git config user.email`; use the GitHub noreply identity for this project, not a personal email or local machine identity.
- Before pushing, scan recent commits with `git log --format='%an <%ae>%n%cn <%ce>' -n 10` and make sure author/committer metadata does not contain personal emails, local hostnames, or machine-specific identities.
- Do not commit secrets, tokens, signing materials, private absolute paths, local-only service URLs, or raw diagnostic logs that may contain personal data. Prefer repo-relative paths and redacted examples in docs.
- If privacy-sensitive data appears in commit history, stop and clean the history before pushing. After rewriting, check `git log --all` and relevant tags/remote refs for the sensitive string, then force-push only the refs that must be corrected.
- Treat local stashes and unpushed branches as user data. Do not delete them just to remove old metadata unless the user explicitly approves.

## Read First

Before changing code, read the relevant parts of:

- `README.md`
- `docs/plan.md`
- `docs/SESSION_SUMMARY.md`
- `MaaFramework-Android/CLAUDE.md`

`docs/SESSION_SUMMARY.md` records important build, runtime, virtual-display, task-config, and packaging pitfalls. It is especially important before touching Root runtime, preview, resource packaging, or MaaFramework task execution.

## Repository Map

- `app/src/main/java/com/maaend/android/ui/`: Compose UI and app view model.
- `app/src/main/java/com/maaend/android/runtime/`: MaaEnd-specific repository/resource adapter.
- `app/src/main/java/com/maaend/android/storage/`: persisted app settings and config import/export support.
- `app/src/main/assets/maa_project_manifest.json`: MaaEnd project manifest; GitHub resource source is `MaaEnd/MaaEnd` branch `v2`, asset root `assets`, with `resource_adb` attached.
- `runtime/`: bundled Android runtime files copied into APK assets/JNI libs by `app/build.gradle.kts`.
- `MaaFramework-Android/framework/`: shared framework library submodule.
- `MaaFramework-Android/runtime/`: framework runtime staging area in the submodule; not the current MaaEnd app runtime source unless Gradle is changed.
- `docs/`: plans, session notes, release/support docs.

Older local framework packages such as `root`, `maa`, `catalog`, `model`, `preview`, `bridge`, AIDL, and native bridge sources were removed from the app module during the split. Do not restore them unless the user explicitly asks to undo the framework extraction.

## Important Files

Start here for MaaEnd app behavior:

1. `app/src/main/java/com/maaend/android/ui/MainViewModel.kt`
2. `app/src/main/java/com/maaend/android/ui/MaaEndApp.kt`
3. `app/src/main/java/com/maaend/android/ui/ProjectInterfaceSupport.kt`
4. `app/src/main/java/com/maaend/android/runtime/PersistentResourceRepositoryManager.kt`
5. `app/src/main/java/com/maaend/android/storage/AppSettingsRepository.kt`
6. `app/src/main/assets/maa_project_manifest.json`
7. `app/build.gradle.kts`
8. `settings.gradle.kts`

For framework/runtime behavior, inspect the submodule:

1. `MaaFramework-Android/framework/src/main/java/com/maaframework/android/session/MaaFrameworkSession.kt`
2. `MaaFramework-Android/framework/src/main/java/com/maaframework/android/session/MaaRuntimeClient.kt`
3. `MaaFramework-Android/framework/src/main/java/com/maaframework/android/catalog/InterfaceCatalogLoader.kt`
4. `MaaFramework-Android/framework/src/main/java/com/maaframework/android/root/RootRuntimeService.kt`
5. `MaaFramework-Android/framework/src/main/java/com/maaframework/android/preview/VirtualDisplayManager.kt`
6. `MaaFramework-Android/framework/src/main/java/com/maaframework/android/runtime/RuntimeBootstrapper.kt`

## Build And Test

Use JDK 21 for this repository unless Gradle/AGP is intentionally upgraded.

```bash
JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
sh ./gradlew :app:assembleDebug
```

Run framework unit tests through the included submodule:

```bash
JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
sh ./gradlew :framework:testDebugUnitTest
```

Install and launch the debug app on the known physical device:

```bash
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
$ADB -s 382b528f install -r -d app/build/outputs/apk/debug/app-debug.apk
$ADB -s 382b528f shell am start -n com.maaend.android.debug/com.maaend.android.MainActivity
```

Debug package/activity:

```text
com.maaend.android.debug/com.maaend.android.MainActivity
```

Release builds use:

```bash
JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
sh ./gradlew :app:assembleRelease
```

## Runtime And Resources

Before debugging empty tasks or runtime failures, check these first:

- `MaaFramework-Android` submodule exists. If missing, run `git submodule update --init --recursive`.
- `runtime/agent/go-service` exists and is an Android executable.
- `runtime/maafw/libMaaFramework.so` exists.
- `runtime/maafw/` contains the required Android MaaFramework shared libraries.
- `app/src/main/assets/maa_project_manifest.json` points to MaaEnd GitHub resources and required paths are present after sync.

After rebuilding `runtime/agent/go-service`, verify:

```bash
file runtime/agent/go-service
```

The output must mention Android's `/system/bin/linker64`. If it mentions `/lib/ld-linux-aarch64.so.1`, it is a Linux ARM64 binary and will not run on Android.

Current on-device resource path for the debug app:

```text
/sdcard/Android/data/com.maaend.android.debug/files/maaframework-resource/maaend/current
```

## Implementation Rules

- Keep changes tightly scoped and avoid unrelated refactors.
- Follow existing Kotlin, Compose, Gradle, and framework API patterns.
- Preserve Root-only behavior unless the user explicitly asks to revisit the product boundary.
- Prefer the upstream MaaEnd resource task flow. Keep `AndroidOpenGame` on the upstream resource task path unless there is a specific Android-only blocker.
- Do not replace the generic catalog-driven task/preset UI with task-specific panels without a strong reason.
- Keep task editing focus and execution selection separate: card click means edit/focus, checkbox means include in run order.
- Preserve config import/export behavior in settings when touching persisted app settings.
- Treat preview surface lifecycle and virtual display lifecycle separately. UI surface loss should not destroy the virtual display unless an explicit stop path does so.
- Landscape preview may letterbox with left/right black bars; prefer correct aspect ratio over stretching.
- Watch for stale Root runtime processes, stale virtual displays, and old log entries before concluding recognition or task logic is broken.
- Android packaging can drop `__Private` pipeline directories. Preserve private-pipeline staging and restore behavior when changing resource packaging.
- If labels show raw keys, inspect the upstream resource locale first, then `MaaFramework-Android/framework/src/main/java/com/maaframework/android/catalog/InterfaceCatalogLoader.kt`.
- If logs disagree, compare timestamps before trusting old `root-runtime.log` or `go-service.log` entries.

## Debugging Checklist

When the app builds but behavior is wrong, check in this order:

1. Build environment: JDK 21 and Android SDK paths.
2. Submodule state: `git submodule status` and `MaaFramework-Android/CLAUDE.md`.
3. Runtime files: `runtime/agent/go-service`, `runtime/maafw/libMaaFramework.so`.
4. Binary format: `file runtime/agent/go-service`.
5. MaaEnd resource sync metadata and the current resource path under app external files.
6. Current Root runtime process and current virtual display id.
7. Game activity display id versus controller/display target.
8. Current log timestamps.
9. Task JSON options, `default_case`, nested `option`, and `pipeline_override`.
10. Private pipeline restore paths for `resource/pipeline/Common/__Private` and `resource_adb/pipeline/Common/__Private`.
