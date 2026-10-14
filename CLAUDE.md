# Project: UltraCore (System Optimizer, Deep Cleaner & Smart File Organizer for Galaxy S25 Ultra)

## Target Environment & CI/CD
- **Device & OS:** Samsung Galaxy S25 Ultra running **Android 17 (One UI 9)**
- **Build System:** GitHub Actions CI/CD (`ubuntu-latest`). Do NOT rely on a pre-existing binary `gradle-wrapper.jar` in git.
- **Toolchain Versions (STRICT for CI stability):**
  - Android Gradle Plugin (AGP): `8.7.3`
  - Kotlin: `2.0.21` (with `org.jetbrains.kotlin.plugin.compose` and `org.jetbrains.kotlin.plugin.serialization`)
  - Gradle Version in CI: `8.10.2`
  - `minSdk = 31`, `compileSdk = 35`, `targetSdk = 35`
  - Java/JVM Target: `17`
- **UI Framework:** Jetpack Compose (Material 3), Pure AMOLED Dark Theme (`#000000` background, `#111113` cards, `#1E1E24` borders, One UI 9 squircle radius `24.dp`)
- **Privileged Execution:** Shizuku API (`dev.rikka.shizuku:api:13.1.5` and `dev.rikka.shizuku:provider:13.1.5`)

## Android 17 (One UI 9) Technical Rules (MUST FOLLOW)
1. **Shizuku AIDL UserService Only:** NEVER call `Shizuku.newProcess()` (it is private in Shizuku 13+). Always bind an AIDL `UserService` (`IShellService.aidl` -> `ShellUserService.kt`) via `Shizuku.bindUserService()`.
2. **Shell Execution inside UserService:** Execute commands via `ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()`. Read stdout with a timeout so the binder thread never hangs.
3. **Restricted Storage Access (`Android/data` & `Android/obb`):**
   - `ShellUserService` runs as `UID 2000 (shell)`, so all scanning and cleaning of `/storage/emulated/0/Android/data` and `/storage/emulated/0/Android/obb` MUST happen inside `ShellUserService`.
   - Public storage (`Download`, `Documents`, `Pictures`, etc.) is managed directly from the app process using `MANAGE_EXTERNAL_STORAGE` (`Environment.isExternalStorageManager()`).
4. **Package Visibility:** Always declare `QUERY_ALL_PACKAGES` in `AndroidManifest.xml` to cross-check `Android/data` directories against installed packages.
5. **Safety & Rollback:** Every file organization operation must support Dry Run preview and save a rollback log (JSON via `kotlinx.serialization`) so the user can undo any batch file move in one tap.
6. **Delete guard:** `ShellUserService.deletePath()` only deletes inside `/storage/emulated/0/` and refuses top-level protected folders (`Android`, `Android/data`, `DCIM`, `Download`, ...). Keep this guard in all future phases.

## Project layout
- Package / applicationId: `com.ultracore.app`
- AIDL: `app/src/main/aidl/com/ultracore/app/shizuku/IShellService.aidl`
- Shizuku bridge: `app/src/main/java/com/ultracore/app/shizuku/` (`ShellUserService`, `ShizukuManager`, `ShizukuState`)
- UI: `app/src/main/java/com/ultracore/app/ui/` (theme, components, screens)
- CI signs debug builds with the committed `app/ultracore-debug.keystore` so every new APK installs over the previous one.

## Phases
- Phase 1 (done, verified on device: uid=2000, ext_data_rw/ext_obb_rw, S938BXXUCDZIF): project skeleton, Shizuku AIDL bridge, status header, CI.
- Phase 2 (done): `OptimizerScreen` + `optimizer/` package (`SystemOptimizerRepository`, `OptimizerViewModel`, `OptimizerModels`).
  - Telemetry: StatFs(/data), /proc/meminfo (RAM, Swap/ZRAM), refreshed every 3 s while STARTED.
  - Modules: pm trim-caches 999G · am kill-all · logcat -b all -c + sm fstrim · per-package `cmd package compile -m speed-profile -f` (Flow progress, cancellable between packages) · cmd package bg-dexopt-job (600 s).
  - One-Tap Ultra Boost order: TrimCaches → FreeRam → LogsAndTrim → ArtCompile.
  - `execCommandWithTimeout(cmd, 1..600)` in AIDL; success/failure detected via `ShellMarkers`.
  - Rule: any AIDL change MUST bump `versionCode`, otherwise Shizuku keeps the old UserService process.
- Phase 3 (done): `CleanerScreen` + `cleaner/` (`CleanerRepository`, `CleanerViewModel`, `CleanerModels`).
  - Scan only, delete after explicit confirmation. Categories: orphan Android/data+obb dirs (via Shizuku; installed set = PackageManager incl. uninstalled-with-data + `pm list packages -u`; aborts if < 50 packages), `.thumbnails`, `*.log/*.tmp/*.temp`, topmost empty folders (never `StoragePaths.PROTECTED_DIRS`), duplicates ≥ 1 MB (size → partial hash → SHA-256, oldest copy kept).
  - Deleted files are reported to MediaScanner.
- Phase 4 (done): `SorterScreen` + `sorter/` (`SorterRepository`, `SorterViewModel`, `SorterModels`).
  - Rules in `filesDir/sort_rules.json`, history in `filesDir/sort_history.json` (last 20 batches), kotlinx.serialization.
  - Only top-level files of the source dir; first matching rule wins; target template {yyyy} {MM} {dd} {ext}; never overwrite (" (1)" suffix); Android/ is off-limits.
  - Dry Run plan → execute → batch saved even if cancelled → undo restores and removes empty created dirs.
- Shared: `storage/StoragePaths.kt` (ROOT, PROTECTED_DIRS, resolveInside, uniqueTarget), `ui/components/Common.kt`.
