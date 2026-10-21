package com.ultracore.app.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ultracore.app.sandbox.SandboxManager
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Invisible activity behind home-screen shortcuts: opens the target screen and closes.
 * Exported (some launchers need that), but it only obeys intents carrying UltraCore's secret token,
 * so other apps cannot use it to open screens with Shizuku's privileges.
 */
class ProxyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val cls = intent.getStringExtra(EXTRA_CLASS)
        val sandbox = intent.getBooleanExtra(EXTRA_SANDBOX, false)
        val token = intent.getStringExtra(EXTRA_TOKEN)
        if (pkg.isNullOrBlank() || (!sandbox && cls.isNullOrBlank()) || token != secret(this)) {
            finish()
            return
        }
        lifecycleScope.launch {
            if (sandbox) {
                // Sandbox shortcut: unfreeze, open, freeze again after the screen turns off.
                val result = SandboxManager.launch(applicationContext, pkg)
                if (!result.ok) Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()
            } else {
                val result = ActivityLauncher.launch(applicationContext, pkg, cls!!)
                if (!result.ok) Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    companion object {
        const val ACTION = "com.ultracore.app.action.OPEN_ACTIVITY"
        private const val EXTRA_PACKAGE = "pkg"
        private const val EXTRA_CLASS = "cls"
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_SANDBOX = "sandbox"

        /** Random per-install secret, created on first use. */
        @Synchronized
        private fun secret(context: Context): String {
            val file = File(context.filesDir, "proxy_token")
            val existing = runCatching { file.readText().trim() }.getOrNull()
            if (!existing.isNullOrEmpty()) return existing
            val created = UUID.randomUUID().toString()
            runCatching { file.writeText(created) }
            return created
        }

        fun intent(context: Context, pkg: String, cls: String): Intent =
            Intent(context, ProxyActivity::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_PACKAGE, pkg)
                .putExtra(EXTRA_CLASS, cls)
                .putExtra(EXTRA_TOKEN, secret(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        /** Shortcut for a sandboxed app. */
        fun sandboxIntent(context: Context, pkg: String): Intent =
            Intent(context, ProxyActivity::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_PACKAGE, pkg)
                .putExtra(EXTRA_SANDBOX, true)
                .putExtra(EXTRA_TOKEN, secret(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
