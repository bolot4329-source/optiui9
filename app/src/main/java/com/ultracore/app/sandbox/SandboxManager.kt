package com.ultracore.app.sandbox

import android.content.Context
import android.content.Intent
import com.ultracore.app.manager.AppManagerRepository
import com.ultracore.app.manager.OpResult
import com.ultracore.app.shizuku.ShellUserService
import com.ultracore.app.shizuku.ShizukuManager
import com.ultracore.app.shizuku.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
                if (s is ShizukuState.Connected && store.state.value.pending.isNotEmpty()) freezePending()
            }
        }
    }

    fun isInSandbox(pkg: String) = pkg in store.state.value.packages

    suspend fun add(pkg: String): OpResult {
        val r = repository.freeze(pkg)
        if (r.ok) store.update { it.copy(packages = (it.packages + pkg).distinct(), pending = it.pending - pkg) }
        return r
    }

    /** Takes the app out of the sandbox and unfreezes it. */
    suspend fun remove(pkg: String): OpResult {
        val r = repository.unfreeze(pkg)
        store.update { it.copy(packages = it.packages - pkg, pending = it.pending - pkg) }
        if (store.state.value.pending.isEmpty()) SandboxService.stop(appContext)
        return r
    }

    fun setDelay(seconds: Int) = store.update { it.copy(delaySeconds = seconds) }

    /** Unfreeze → open → remember to freeze after the screen goes off. */
    suspend fun launch(context: Context, pkg: String): OpResult {
        if (!awaitShizuku()) return OpResult(false, "Shizuku не подключён — запустите Shizuku")
        val unfreeze = repository.unfreeze(pkg)
        if (!unfreeze.ok) return unfreeze
        store.update { it.copy(pending = (it.pending + pkg).distinct()) }
        SandboxService.start(context)

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

    /** Freezes every pending app. Returns how many are still pending. */
    suspend fun freezePending(): Int {
        freezeMutex.withLock {
            if (!ShizukuManager.isConnected) return store.state.value.pending.size
            for (pkg in store.state.value.pending) {
                val r = repository.freeze(pkg)
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
