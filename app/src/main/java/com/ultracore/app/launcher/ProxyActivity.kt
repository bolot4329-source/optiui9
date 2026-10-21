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
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Invisible activity behind home-screen shortcuts: opens the target screen and closes.
 * Exported (some launchers need that), but every shortcut carries an HMAC signature of exactly
 * its own target, made with a per-install secret that never leaves UltraCore. Another app that
 * sees a shortcut can only open that same target — never anything else with Shizuku's privileges.
 */
class ProxyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val cls = intent.getStringExtra(EXTRA_CLASS)
        val sandbox = intent.getBooleanExtra(EXTRA_SANDBOX, false)
        val token = intent.getStringExtra(EXTRA_TOKEN)
        if (pkg.isNullOrBlank() || (!sandbox && cls.isNullOrBlank()) || token == null ||
            !MessageDigest.isEqual(token.toByteArray(), sign(this, pkg, cls, sandbox).toByteArray())
        ) {
            finish()
            return
        }
        lifecycleScope.launch {
            if (sandbox) {
                // Sandbox shortcut: unfreeze, open, freeze again after the screen turns off.
                // The activity's own context: starting other apps from applicationContext can be
                // blocked by background-activity-launch rules on Android 14+.
                val result = SandboxManager.launch(this@ProxyActivity, pkg)
                if (!result.ok) Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()
            } else {
                val result = ActivityLauncher.launch(this@ProxyActivity, pkg, cls!!)
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

        /** HMAC-SHA256 of the shortcut target, keyed with the install secret. */
        private fun sign(context: Context, pkg: String, cls: String?, sandbox: Boolean): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret(context).toByteArray(), "HmacSHA256"))
            val data = "$pkg|${cls.orEmpty()}|$sandbox".toByteArray()
            return mac.doFinal(data).joinToString("") { "%02x".format(it) }
        }

        fun intent(context: Context, pkg: String, cls: String): Intent =
            Intent(context, ProxyActivity::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_PACKAGE, pkg)
                .putExtra(EXTRA_CLASS, cls)
                .putExtra(EXTRA_TOKEN, sign(context, pkg, cls, false))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        /** Shortcut for a sandboxed app. */
        fun sandboxIntent(context: Context, pkg: String): Intent =
            Intent(context, ProxyActivity::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_PACKAGE, pkg)
                .putExtra(EXTRA_SANDBOX, true)
                .putExtra(EXTRA_TOKEN, sign(context, pkg, null, true))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
