package com.ultracore.app.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.ultracore.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Single entry point to Shizuku: tracks its state, requests permission
 * and binds [ShellUserService] (UID 2000) through AIDL.
 *
 * Call [init] once from Application.onCreate().
 */
object ShizukuManager {

    private const val TAG = "UltraCore/Shizuku"
    private const val REQUEST_CODE = 1702
    private const val BIND_TIMEOUT_MS = 15_000L

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<ShizukuState>(ShizukuState.NotRunning)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    @Volatile
    private var service: IShellService? = null
    private var bound = false
    private var initialized = false
    private var bindTimeoutJob: Job? = null

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, ShellUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            bindTimeoutJob?.cancel()
            if (binder == null || !binder.pingBinder()) {
                _state.value = ShizukuState.Error("UserService вернул пустой binder")
                return
            }
            val shell = IShellService.Stub.asInterface(binder)
            service = shell
            scope.launch {
                val uidInfo = runCatching {
                    withContext(Dispatchers.IO) { shell.execCommand("id") }
                }.getOrNull()
                    ?.lineSequence()
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()
                    ?: "uid=${runCatching { Shizuku.getUid() }.getOrDefault(-1)}"
                _state.value = ShizukuState.Connected(uidInfo)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "UserService disconnected")
            service = null
            bound = false
            refresh()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refresh()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        service = null
        bound = false
        bindTimeoutJob?.cancel()
        _state.value = ShizukuState.NotRunning
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQUEST_CODE) return@OnRequestPermissionResultListener
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                bindService()
            } else {
                _state.value = ShizukuState.PermissionDenied
            }
        }

    fun init() {
        if (initialized) return
        initialized = true
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refresh()
    }

    /** Re-evaluates state; binds the service if everything is ready. Safe to call often. */
    fun refresh() {
        val blocked: ShizukuState? = try {
            when {
                !Shizuku.pingBinder() -> ShizukuState.NotRunning
                Shizuku.isPreV11() -> ShizukuState.Error("Shizuku слишком старый — обновите приложение Shizuku")
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuState.PermissionDenied
                else -> null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "refresh failed", t)
            ShizukuState.NotRunning
        }

        if (blocked != null) {
            _state.value = blocked
            return
        }
        if (service?.asBinder()?.pingBinder() == true) return
        bindService()
    }

    /** Button "Разрешить": asks Shizuku for permission, or reconnects if already granted. */
    fun requestPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                _state.value = ShizukuState.NotRunning
                return
            }
            if (Shizuku.isPreV11()) {
                refresh()
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                reconnect()
            } else {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "requestPermission failed", t)
            _state.value = ShizukuState.Error(t.message ?: "Не удалось запросить разрешение")
        }
    }

    /** Button "Подключить": drops the old binding and binds again. */
    fun reconnect() {
        bindTimeoutJob?.cancel()
        if (bound) {
            runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
        }
        bound = false
        service = null
        refresh()
    }

    private fun bindService() {
        if (bound) return
        _state.value = ShizukuState.Connecting
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
            bound = true
            bindTimeoutJob?.cancel()
            bindTimeoutJob = scope.launch {
                delay(BIND_TIMEOUT_MS)
                if (_state.value is ShizukuState.Connecting) {
                    _state.value = ShizukuState.Error(
                        "Тайм-аут подключения. Перезапустите Shizuku и нажмите «Подключить»"
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "bindUserService failed", t)
            bound = false
            _state.value = ShizukuState.Error(t.message ?: "Не удалось запустить UserService")
        }
    }

    private fun requireService(): IShellService =
        service ?: throw IllegalStateException("Shizuku не подключён")

    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        requireService().execCommand(command)
    }

    suspend fun listDirectory(path: String): List<String> = withContext(Dispatchers.IO) {
        requireService().listDirectory(path) ?: emptyList()
    }

    suspend fun deletePath(path: String): Boolean = withContext(Dispatchers.IO) {
        requireService().deletePath(path)
    }
}
