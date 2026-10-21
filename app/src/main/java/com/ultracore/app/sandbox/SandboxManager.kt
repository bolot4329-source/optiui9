package com.ultracore.app.sandbox

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.ultracore.app.manager.AppManagerRepository
import com.ultracore.app.manager.OpResult
import com.ultracore.app.shizuku.ShellUserService
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.shizuku.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * «Песочница»: rarely used apps stay frozen. Opening one unfreezes it; after the screen
 * turns off (plus a delay) it is frozen again. If Shizuku is gone at that moment, the app
 * stays in `pending` and is frozen as soon as Shizuku reconnects.
 */
object SandboxManager {

    private lateinit var appContext: Context
    private lateinit var store: SandboxStore
    private lateinit var repository: AppManagerRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val freezeMutex = Mutex()

    val state: StateFlow<SandboxState> get() = store.state

    fun init(context: Context) {
        if (::store.isInitialized) return
        appContext = context.applicationContext
        store = SandboxStore(appContext)
        repository = AppManagerRepository(appContext)
        scope.launch {
            ShizukuManager.state.collect { s ->
                if (s is ShizukuState.Connected && store.state.value.pending.isNotEmpty()) {
                    // Shizuku came back: freeze leftovers only if the screen is off,
                    // otherwise the user may be using the app right now — let the service wait.
                    if (isScreenOn()) SandboxService.start(appContext) else freezePending()
                }
            }
        }
    }

    fun isInSandbox(pkg: String) = pkg in store.state.value.packages

    /** Puts the app to sleep the way chosen in settings (pause or full freeze). */
    private suspend fun sleep(pkg: String): OpResult =
        if (store.state.value.mode == SandboxState.MODE_DISABLE) repository.freeze(pkg) else repository.suspendApp(pkg)

    /** Wakes the app whatever way it was put to sleep (mode may have changed since). */
    private suspend fun wake(pkg: String): OpResult {
        // Both must succeed: `pm unsuspend` on a frozen app «succeeds» while it stays frozen.
        val unsuspend = repository.unsuspendApp(pkg)
        val enable = repository.unfreeze(pkg)
        return when {
            unsuspend.ok && enable.ok -> OpResult(true, "Готово")
            !enable.ok -> enable
            else -> unsuspend
        }
    }

    suspend fun add(pkg: String): OpResult {
        val r = sleep(pkg)
        if (r.ok) store.update { it.copy(packages = (it.packages + pkg).distinct(), pending = it.pending - pkg) }
        return r
    }

    /** Takes the app out of the sandbox and unfreezes it. */
    suspend fun remove(pkg: String): OpResult {
        val r = wake(pkg)
        // Keep it in the list if waking failed — otherwise nobody remembers it is asleep.
        if (r.ok) store.update { it.copy(packages = it.packages - pkg, pending = it.pending - pkg) }
        if (store.state.value.pending.isEmpty()) SandboxService.stop(appContext)
        return r
    }

    fun setDelay(seconds: Int) = store.update { it.copy(delaySeconds = seconds) }

    fun setMode(mode: String) = store.update { it.copy(mode = mode) }

    /** Wakes every sandboxed app (before a trip or a reboot); they stay in the sandbox list. */
    suspend fun wakeAll(): List<Pair<String, String>> = wakeEach(removeFromSandbox = false)

    /**
     * Wakes every sandboxed app. Only apps that really woke up leave `pending`
     * (and the sandbox list when [removeFromSandbox]); failed ones stay listed so they are not lost.
     */
    private suspend fun wakeEach(removeFromSandbox: Boolean): List<Pair<String, String>> {
        val failures = mutableListOf<Pair<String, String>>()
        val woken = mutableSetOf<String>()
        for (pkg in store.state.value.packages) {
            val r = wake(pkg)
            if (r.ok) woken += pkg else failures += pkg to r.message
        }
        store.update {
            it.copy(
                packages = if (removeFromSandbox) it.packages - woken else it.packages,
                pending = it.pending - woken,
            )
        }
        if (store.state.value.pending.isEmpty()) SandboxService.stop(appContext)
        return failures
    }

    /** Puts every sandboxed app to sleep right now. */
    suspend fun sleepAll(): List<Pair<String, String>> {
        val failures = mutableListOf<Pair<String, String>>()
        val slept = mutableSetOf<String>()
        for (pkg in store.state.value.packages) {
            val r = sleep(pkg)
            if (r.ok) slept += pkg else failures += pkg to r.message
        }
        store.update { it.copy(pending = it.pending - slept) }
        if (store.state.value.pending.isEmpty()) SandboxService.stop(appContext)
        return failures
    }

    /** Wakes everything and empties the sandbox (used by «Вернуть телефон как было»). */
    suspend fun clearAll(): List<Pair<String, String>> = wakeEach(removeFromSandbox = true)

    /** Unfreeze → open → remember to freeze after the screen goes off. */
    suspend fun launch(context: Context, pkg: String): OpResult {
        // Start the service first, while the calling screen is surely in the foreground:
        // Android 14+ refuses to start foreground services from the background.
        store.update { it.copy(pending = (it.pending + pkg).distinct()) }
        SandboxService.start(context)
        if (!awaitShizuku()) {
            undoPending(pkg)
            return OpResult(false, "Shizuku не подключён — запустите Shizuku")
        }
        val unfreeze = wake(pkg)
        if (!unfreeze.ok) {
            undoPending(pkg)
            return unfreeze
        }

        // The launcher entry may appear a moment after enabling.
        var intent: Intent? = null
        repeat(10) {
            intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) return@repeat
            delay(150)
        }
        val launch = intent
        return if (launch != null) {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            OpResult(true, "Открыто")
        } else {
            val r = ShizukuManager.execResult(
                "monkey -p ${ShellUserService.shellQuote(pkg)} -c android.intent.category.LAUNCHER 1",
                20,
            )
            if (r.isSuccess) OpResult(true, "Открыто") else OpResult(false, "Приложение разморожено, но у него нет экрана запуска")
        }
    }

    private fun undoPending(pkg: String) {
        store.update { it.copy(pending = it.pending - pkg) }
        if (store.state.value.pending.isEmpty()) SandboxService.stop(appContext)
    }

    fun isScreenOn(): Boolean =
        appContext.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    /** Freezes every pending app while the screen stays off. Returns how many are still pending. */
    suspend fun freezePending(): Int {
        freezeMutex.withLock {
            if (!ShizukuManager.isConnected) return store.state.value.pending.size
            for (pkg in store.state.value.pending) {
                // Screen on again (or the job was cancelled by SCREEN_ON) — stop right here.
                currentCoroutineContext().ensureActive()
                if (isScreenOn()) break
                val r = sleep(pkg)
                if (r.ok || pkg !in store.state.value.packages) {
                    store.update { it.copy(pending = it.pending - pkg) }
                }
            }
            val left = store.state.value.pending.size
            if (left == 0) SandboxService.stop(appContext)
            return left
        }
    }

    private suspend fun awaitShizuku(): Boolean {
        if (ShizukuManager.isConnected) return true
        ShizukuManager.refresh()
        return withTimeoutOrNull(8_000) { ShizukuManager.state.first { it is ShizukuState.Connected } } != null
    }
}
