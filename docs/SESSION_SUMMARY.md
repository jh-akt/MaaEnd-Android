# MaaEnd Android Session Summary

Date: 2026-04-19
Scope: Android Root-only MVP scaffold, prepared for handoff to another device/session.

## 1. What was added

An Android host app was scaffolded under this standalone project with:

- Gradle project files
- single `:app` module
- Root bootstrap chain
- AIDL contract for root runtime IPC
- runtime asset staging hook
- catalog parsing from `assets/interface.json`
- minimal Compose UI for task / preset / runtime state
- runtime bootstrap, diagnostics export, and fallback `AndroidOpenGame`

The supporting helper script below was also added:

- `tools/prepare_android_runtime.py`

## 2. Current project state

The Android project is currently configured to use:

- AGP `8.2.2`
- Kotlin `1.9.0`
- Gradle wrapper `8.2`
- compileSdk / targetSdk `34`
- minSdk `30`
- Compose enabled
- `libsu` from JitPack

Relevant files:

- `build.gradle.kts`
- `app/build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`

## 3. Build issues already handled

The following problems were hit and already fixed in the repo:

1. Windows non-ASCII path check from AGP
   - fixed by adding `android.overridePathCheck=true`

2. Missing JitPack repository for `libsu`
   - fixed by adding `maven("https://jitpack.io")` to `settings.gradle.kts`

3. AIDL transaction id mismatch
   - fixed by removing the custom method id from `IRootRuntimeService.aidl`

## 4. Last known compile status

The last build error reported by the user before this summary was:

- `:app:compileDebugAidl`
- `You must either assign id's to all methods or to none of them.`

That error has already been fixed in the repository.

Build was **not re-run after the fix** in this session because the task changed to cleanup + handoff summary.

## 5. What needs to be verified next

On the next device / next session, the first thing to do is rerun:

```powershell
cd C:\workspace\MaaEnd-Android
C:\gradle\gradle-8.2\bin\gradle.bat assembleDebug
```

If build succeeds, continue with:

```powershell
adb start-server
adb connect 127.0.0.1:16384
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.maaend.android/.MainActivity
```

## 6. Runtime packaging prerequisites

The app expects staged runtime artifacts under:

- `runtime/agent/go-service`
- `runtime/maafw/`

If these are missing:

- the app should still boot
- Root bootstrap should still be testable
- only the fallback `AndroidOpenGame` path is expected to be usable

To stage runtime artifacts:

```powershell
python tools/prepare_android_runtime.py --maaend-root <path-to-maaend-repo> --go-exe <path-to-go> --maafw-dir <path-to-maafw-android-runtime>
```

## 7. Recommended next debugging order

1. Rerun `assembleDebug`
2. Fix any remaining Kotlin / Java compile errors
3. Install APK to MuMu
4. Verify app launch
5. Verify Root bootstrap connection
6. Verify `prepareRuntime()`
7. Verify fallback `AndroidOpenGame`
8. Only then move on to full Maa runtime integration

## 8. Important files for continuation

Primary Android entrypoints:

- `app/src/main/java/com/maaend/android/MainActivity.kt`
- `app/src/main/java/com/maaend/android/ui/MainViewModel.kt`
- `app/src/main/java/com/maaend/android/ui/MaaEndApp.kt`

Root runtime chain:

- `app/src/main/java/com/maaend/android/root/RootRuntimeConnector.kt`
- `app/src/main/java/com/maaend/android/root/RootRuntimeService.kt`
- `app/src/main/java/com/maaend/android/root/RootServiceStarter.java`
- `app/src/main/java/com/maaend/android/root/RootUserService.java`
- `app/src/main/aidl/com/maaend/android/ipc/IRootRuntimeService.aidl`

Catalog / runtime setup:

- `app/src/main/java/com/maaend/android/catalog/InterfaceCatalogLoader.kt`
- `app/src/main/java/com/maaend/android/runtime/RuntimeBootstrapper.kt`
- `tools/prepare_android_runtime.py`

## 9. GitHub submission note

The repo was cleaned of generated Android build output during this session.

Files intentionally added/changed for submission readiness:

- `.gitignore`
- project root
- `tools/prepare_android_runtime.py`
- `docs/plan.md`

Before submitting, rerun:

```powershell
git status
```

and make sure no local build output or staged runtime binaries are included unintentionally.
