package com.ultracore.app.launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import com.ultracore.app.core.ErrorClassifier
import com.ultracore.app.shizuku.ShellResult
import com.ultracore.app.shizuku.ShellUserService
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.shizuku.ShizukuMode
import com.ultracore.app.shizuku.ShizukuState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

data class LaunchResult(val ok: Boolean, val message: String)

/**
 * Opens an activity the most direct way that is allowed:
 * normal Intent → `am start` through Shizuku → only with root for non-exported screens.
 */
object ActivityLauncher {

    suspend fun launch(context: Context, packageName: String, className: String): LaunchResult {
        val info = findActivity(context, packageName, className)
            ?: return LaunchResult(false, "Экран не найден — возможно, приложение обновилось или удалено")
        val exported = info.exported
        val permission = info.permission

        if (exported && permission == null) {
            val direct = startDirect(context, packageName, className)
            if (direct) return LaunchResult(true, "Открыто")
            // Fall through to the shell — some screens refuse ordinary callers.
        }

        if (!exported && ShizukuManager.mode != ShizukuMode.ROOT) {
            return LaunchResult(false, "Закрытый экран: Android разрешает открыть его только с Root")
        }

        if (!awaitShizuku()) return LaunchResult(false, "Shizuku не подключён — запустите Shizuku")
        val cmd = "am start -n " + ShellUserService.shellQuote("$packageName/$className")
        val result = ShizukuManager.execResult(cmd, 20)
        return if (isAmStartSuccess(result, packageName, className)) {
            LaunchResult(true, "Открыто через Shizuku")
        } else {
            val e = ErrorClassifier.classify(failure(result))
            LaunchResult(false, if (e.detail.isNotBlank()) "${e.message}\n${e.detail}" else e.message)
        }
    }

    private fun startDirect(context: Context, pkg: String, cls: String): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(pkg, cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: SecurityException) {
        false
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    /**
     * `am start` prints errors but often exits with 0. The component name is removed first,
     * so a screen called e.g. «ErrorActivity» is not mistaken for a failure.
     */
    private fun isAmStartSuccess(r: ShellResult, pkg: String, cls: String): Boolean {
        if (!r.isSuccess) return false
        val text = r.combined
            .replace(cls, "", ignoreCase = true)
            .replace(pkg, "", ignoreCase = true)
            .lowercase()
        return "error:" !in text && "error type" !in text && "exception" !in text
    }

    private fun failure(r: ShellResult): ShellResult =
        if (r.isSuccess) ShellResult(1, r.stdout, r.stderr, r.truncated, false) else r

    private suspend fun awaitShizuku(): Boolean {
        if (ShizukuManager.isConnected) return true
        ShizukuManager.refresh()
        return withTimeoutOrNull(8_000) { ShizukuManager.state.first { it is ShizukuState.Connected } } != null
    }

    @Suppress("DEPRECATION")
    private fun findActivity(context: Context, pkg: String, cls: String): ActivityInfo? = runCatching {
        val pm = context.packageManager
        val component = ComponentName(pkg, cls)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
        } else {
            pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
    }.getOrNull()
}
