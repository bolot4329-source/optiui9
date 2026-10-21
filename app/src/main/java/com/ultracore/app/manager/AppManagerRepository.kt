package com.ultracore.app.manager

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import com.ultracore.app.core.ErrorClassifier
import com.ultracore.app.shizuku.ShellResult
import com.ultracore.app.shizuku.ShellUserService
import com.ultracore.app.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * App management through standard `pm` / `cmd package` commands and public APIs only.
 * Important system parts (launcher, phone, SMS, keyboard, settings, SystemUI…) are protected
 * from freezing and removal — they are detected by their role on THIS phone, not by brand.
 */
class AppManagerRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm = appContext.packageManager

    init {
        // Cheap guard: the change list must be loaded before any change is recorded.
        AppChangeStore.init(appContext)
    }

    // ------------------------------------------------------------------ list

    suspend fun loadApps(): List<ManagedApp> = withContext(Dispatchers.IO) {
        val protectedMap = protectedPackages()
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
        installedPackages(flags).mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null
            val persistent = app.flags and ApplicationInfo.FLAG_PERSISTENT != 0
            ManagedApp(
                packageName = info.packageName,
                label = runCatching { app.loadLabel(pm).toString() }.getOrDefault(info.packageName),
                isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                enabled = app.enabled,
                suspended = app.flags and ApplicationInfo.FLAG_SUSPENDED != 0,
                installed = app.flags and ApplicationInfo.FLAG_INSTALLED != 0,
                versionName = info.versionName.orEmpty(),
                sourceDir = app.sourceDir,
                splitDirs = app.splitSourceDirs?.toList().orEmpty(),
                protectedReason = protectedMap[info.packageName]
                    ?: if (persistent) "постоянный системный процесс" else null,
            )
        }.sortedBy { it.label.lowercase() }
    }

    /** package → reason. Found by role on this device. */
    private fun protectedPackages(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        fun put(pkg: String?, reason: String) {
            if (!pkg.isNullOrBlank() && pkg !in map) map[pkg] = reason
        }
        put(appContext.packageName, "это UltraCore")
        put(ShizukuManager.SHIZUKU_PACKAGE, "нужен для работы UltraCore")
        put("android", "ядро системы Android")
        put("com.android.systemui", "системный интерфейс (шторка, панель навигации)")
        put(resolvePackage(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)), "рабочий стол по умолчанию")
        put(resolvePackage(Intent(Settings.ACTION_SETTINGS)), "приложение «Настройки»")
        put(runCatching { appContext.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull(), "приложение «Телефон»")
        put(runCatching { Telephony.Sms.getDefaultSmsPackage(appContext) }.getOrNull(), "приложение для SMS")
        put(
            runCatching {
                Settings.Secure.getString(appContext.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                    ?.substringBefore('/')
            }.getOrNull(),
            "клавиатура по умолчанию",
        )
        return map
    }

    @Suppress("DEPRECATION")
    private fun resolvePackage(intent: Intent): String? = runCatching {
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()?.takeIf { it != "android" }

    @Suppress("DEPRECATION")
    private fun installedPackages(flags: Int): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getInstalledPackages(flags)
        }

    // ------------------------------------------------------------------ state changes

    // Every successful change is recorded in AppChangeStore («Вернуть всё», export).

    suspend fun freeze(pkg: String): OpResult =
        run("pm disable-user --user 0 ${q(pkg)}", pkg, okWhen = { "disabled" in it || it.isBlank() })
            .also { if (it.ok) AppChangeStore.update { c -> c.copy(frozen = (c.frozen + pkg).distinct()) } }

    suspend fun unfreeze(pkg: String): OpResult =
        run("pm enable --user 0 ${q(pkg)}", pkg, okWhen = { "enabled" in it || it.isBlank() })
            .also { if (it.ok) AppChangeStore.update { c -> c.copy(frozen = c.frozen - pkg) } }

    /** Pause: the icon stays (grey), widgets and data stay, but the app cannot run or notify. */
    // AOSP always prints «Package … new suspended state: true/false»; anything else is a failure.
    suspend fun suspendApp(pkg: String): OpResult =
        run("pm suspend --user 0 ${q(pkg)}", pkg, okWhen = { "state: true" in it })
            .also { if (it.ok) AppChangeStore.update { c -> c.copy(suspended = (c.suspended + pkg).distinct()) } }

    suspend fun unsuspendApp(pkg: String): OpResult =
        run("pm unsuspend --user 0 ${q(pkg)}", pkg, okWhen = { "state: false" in it })
            .also { if (it.ok) AppChangeStore.update { c -> c.copy(suspended = c.suspended - pkg) } }

    /** Removes an app for the current user only; the APK stays in the system, so it can be restored. */
    suspend fun uninstallForUser(pkg: String): OpResult =
        run("pm uninstall -k --user 0 ${q(pkg)}", pkg, okWhen = { "success" in it })
            .also { if (it.ok) AppChangeStore.update { c -> c.copy(removed = (c.removed + pkg).distinct()) } }

    suspend fun restore(pkg: String): OpResult =
        run("cmd package install-existing --user 0 ${q(pkg)}", pkg, okWhen = { "installed" in it || "success" in it })
            .also { if (it.ok) AppChangeStore.update { c -> c.copy(removed = c.removed - pkg) } }

    /**
     * Background limit without freezing: forbids running in the background and puts the app
     * into the strictest standby bucket. Push notifications still arrive. The original state is
     * saved before the first change so «Разрешить фон» restores it exactly.
     */
    suspend fun restrictBackground(pkg: String): OpResult {
        if (!ShizukuManager.isConnected) return OpResult(false, "Shizuku не подключён")
        val backupCreatedNow = pkg !in AppChangeStore.state.value.restricted
        if (backupCreatedNow) {
            val appop = ShizukuManager.execResult("cmd appops get --user 0 ${q(pkg)} RUN_ANY_IN_BACKGROUND", 20)
            val mode = Regex("RUN_ANY_IN_BACKGROUND:\\s*(\\w+)").find(appop.combined)?.groupValues?.get(1) ?: "default"
            val bucket = ShizukuManager.execResult("am get-standby-bucket --user 0 ${q(pkg)}", 20)
                .stdout.trim().lineSequence().firstOrNull().orEmpty()
            AppChangeStore.update { c -> c.copy(restricted = c.restricted + (pkg to BackgroundOriginal(mode, bucket))) }
        }
        val op = run("cmd appops set --user 0 ${q(pkg)} RUN_ANY_IN_BACKGROUND ignore", pkg, okWhen = { true })
        if (!op.ok) {
            // Drop only a backup made in this call — an older one still holds the true original.
            if (backupCreatedNow) AppChangeStore.update { c -> c.copy(restricted = c.restricted - pkg) }
            return op
        }
        val bucket = run("am set-standby-bucket --user 0 ${q(pkg)} restricted", pkg, okWhen = { true })
        return if (bucket.ok) OpResult(true, "Готово") else OpResult(true, "Фон запрещён; режим энергосбережения прошивка не дала изменить")
    }

    suspend fun allowBackground(pkg: String): OpResult {
        if (!ShizukuManager.isConnected) return OpResult(false, "Shizuku не подключён")
        val original = AppChangeStore.state.value.restricted[pkg] ?: BackgroundOriginal("default", "")
        val mode = original.appop.takeIf { it in setOf("allow", "ignore", "deny", "default", "foreground") } ?: "default"
        val op = run("cmd appops set --user 0 ${q(pkg)} RUN_ANY_IN_BACKGROUND $mode", pkg, okWhen = { true })
        if (!op.ok) return op
        // 5 = exempted (system decides) — cannot be set with set-standby-bucket, leave it to the system.
        val bucket = original.bucket.trim()
        if (bucket.isNotEmpty() && bucket != "5") {
            run("am set-standby-bucket --user 0 ${q(pkg)} ${bucketName(bucket)}", pkg, okWhen = { true })
        }
        AppChangeStore.update { c -> c.copy(restricted = c.restricted - pkg) }
        return OpResult(true, "Готово")
    }

    /** Installed for this user, removed for this user (restorable) or disabled — anything but gone. */
    @Suppress("DEPRECATION")
    fun isOnPhone(pkg: String): Boolean = runCatching {
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(pkg, flags)
        }
        true
    }.getOrDefault(false)

    /** `am get-standby-bucket` prints a number; `set` wants a name. */
    private fun bucketName(raw: String): String = when (raw.trim().toIntOrNull()) {
        5, 10 -> "active"
        20 -> "working_set"
        30 -> "frequent"
        40 -> "rare"
        45 -> "restricted"
        else -> raw.trim().takeIf { it in setOf("active", "working_set", "frequent", "rare", "restricted") } ?: "active"
    }

    private suspend fun run(command: String, pkg: String, okWhen: (String) -> Boolean): OpResult {
        if (!ShizukuManager.isConnected) return OpResult(false, "Shizuku не подключён")
        val r = ShizukuManager.execResult(command, 60)
        // The package name is echoed in the output; remove it so names containing
        // «error» or «failure» are not mistaken for an error.
        val out = r.combined.replace(pkg, "", ignoreCase = true).lowercase()
        val failedText = "error:" in out || "exception" in out || "failure [" in out
        return if (r.isSuccess && !failedText && okWhen(out)) {
            OpResult(true, "Готово")
        } else {
            OpResult(false, describe(r))
        }
    }

    private fun describe(r: ShellResult): String {
        val e = ErrorClassifier.classify(if (r.isSuccess) ShellResult(1, r.stdout, r.stderr, r.truncated, false) else r)
        return if (e.detail.isNotBlank()) "${e.message}\n${e.detail}" else e.message
    }

    // ------------------------------------------------------------------ backup

    /** Copies the APK (or all split APKs as .apks) to Download/UltraCore/Backups. Returns the file name. */
    suspend fun backup(app: ManagedApp): OpResult = withContext(Dispatchers.IO) {
        val base = app.sourceDir?.let(::File)
        if (base == null || !base.canRead()) {
            return@withContext OpResult(false, "Файл приложения недоступен для чтения")
        }
        val splits = app.splitDirs.map(::File).filter { it.canRead() }
        val isBundle = splits.isNotEmpty()
        val safeLabel = app.label.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").trim().ifEmpty { app.packageName }
        val version = app.versionName.replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
        val name = "${safeLabel}_$version" + if (isBundle) ".apks" else ".apk"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isBundle) "application/zip" else "application/vnd.android.package-archive")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/UltraCore/Backups")
        }
        val resolver = appContext.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext OpResult(false, "Не удалось создать файл в «Загрузках»")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!isBundle) {
                    FileInputStream(base).use { it.copyTo(out) }
                } else {
                    ZipOutputStream(out).use { zip ->
                        (listOf(base) + splits).forEach { file ->
                            zip.putNextEntry(ZipEntry(file.name))
                            FileInputStream(file).use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            } ?: throw IllegalStateException("Нет доступа к файлу")
            OpResult(true, "Download/UltraCore/Backups/$name")
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            OpResult(false, "Ошибка бэкапа: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    // ------------------------------------------------------------------ install

    /**
     * Installs .apk / .apks / .xapk-like zip with a `pm install-create/write/commit` session.
     * Files are staged in UltraCore's own Android/data folder, which shell can read.
     */
    suspend fun install(uri: Uri, options: InstallOptions, onStep: (String) -> Unit): OpResult = withContext(Dispatchers.IO) {
        if (!ShizukuManager.isConnected) return@withContext OpResult(false, "Shizuku не подключён")
        val dir = File(appContext.getExternalFilesDir(null) ?: return@withContext OpResult(false, "Нет доступа к памяти"), "install")
        dir.deleteRecursively()
        dir.mkdirs()
        try {
            onStep("Копирую файл…")
            val input = File(dir, "input.bin")
            appContext.contentResolver.openInputStream(uri)?.use { src ->
                input.outputStream().use { src.copyTo(it) }
            } ?: return@withContext OpResult(false, "Не удалось открыть файл")

            val apks = unpack(input, dir)
                ?: return@withContext OpResult(false, "Файл не похож на APK или архив с APK (.apks)")

            onStep("Создаю сессию установки…")
            val flags = buildString {
                append(" -r")
                if (options.allowDowngrade) append(" -d")
                if (options.asPlayStore) append(" -i com.android.vending")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) append(" --bypass-low-target-sdk-block")
            }
            val create = ShizukuManager.execResult("pm install-create$flags", 60)
            val session = Regex("\\[(\\d+)]").find(create.combined)?.groupValues?.get(1)
                ?: return@withContext OpResult(false, describe(create))

            apks.forEachIndexed { index, file ->
                onStep("Передаю ${index + 1} из ${apks.size}…")
                val name = file.nameWithoutExtension.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "part$index" }
                // The bytes go through a pipe: shell reads the file (it can read Android/data),
                // system_server only gets the pipe. Passing the path or a redirected FUSE file
                // descriptor can be refused by SELinux on Android 14+.
                val write = ShizukuManager.execResult(
                    "cat ${q(file.absolutePath)} | pm install-write -S ${file.length()} $session ${q(name)} -",
                    300,
                )
                if (!write.isSuccess || "success" !in write.combined.lowercase()) {
                    ShizukuManager.execResult("pm install-abandon $session", 30)
                    return@withContext OpResult(false, describe(write))
                }
            }

            onStep("Устанавливаю…")
            val commit = ShizukuManager.execResult("pm install-commit $session", 300)
            if (commit.isSuccess && "success" in commit.combined.lowercase()) {
                OpResult(true, "Установлено")
            } else {
                OpResult(false, describe(commit))
            }
        } catch (t: Throwable) {
            OpResult(false, "Ошибка установки: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Single APK → base.apk; archive with .apk files inside → split_N.apk list; otherwise null. */
    private fun unpack(input: File, dir: File): List<File>? {
        val isSingleApk = runCatching {
            ZipFile(input).use { it.getEntry("AndroidManifest.xml") != null }
        }.getOrNull() ?: return null
        if (isSingleApk) {
            // The ZipFile is closed before renaming.
            val apk = File(dir, "base.apk")
            return listOf(if (input.renameTo(apk)) apk else input)
        }
        return runCatching {
            ZipFile(input).use { z ->
                val apkEntries = z.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                    .toList()
                apkEntries.mapIndexed { i, entry ->
                    val out = File(dir, "split_$i.apk")
                    z.getInputStream(entry).use { src -> out.outputStream().use { src.copyTo(it) } }
                    out
                }
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun q(value: String) = ShellUserService.shellQuote(value)
}
