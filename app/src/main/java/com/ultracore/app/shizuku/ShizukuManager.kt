package com.ultracore.app.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.ultracore.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import rikka.shizuku.Shizuku

/**
 * Single entry point to Shizuku: tracks its state, requests permission
 * and binds [ShellUserService] (UID 2000) through AIDL.
 *
 * Call [init] once from Application.onCreate().
 * Works the same on any phone: only the public Shizuku API and standard shell are used.
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
    private var appContext: Context? = null
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
                val mode = if (runCatching { Shizuku.getUid() }.getOrDefault(-1) == 0) {
                    ShizukuMode.ROOT
                } else {
                    ShizukuMode.ADB
                }
                _state.value = ShizukuState.Connected(uidInfo, mode)
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
        _state.value = notRunningState()
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

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refresh()
    }

    /** Re-evaluates state; binds the service if everything is ready. Safe to call often. */
    fun refresh() {
        val blocked: ShizukuState? = try {
            when {
                !Shizuku.pingBinder() -> notRunningState()
                Shizuku.isPreV11() -> ShizukuState.Error("Shizuku слишком старый — обновите приложение Shizuku")
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuState.PermissionDenied
                else -> null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "refresh failed", t)
            notRunningState()
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
                _state.value = notRunningState()
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

    /** Shizuku binder is dead: either the app is missing or its service is stopped. */
    private fun notRunningState(): ShizukuState =
        if (isShizukuInstalled()) ShizukuState.NotRunning else ShizukuState.NotInstalled

    fun isShizukuInstalled(): Boolean {
        val pm = appContext?.packageManager ?: return true
        return try {
            pm.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Current mode, or null when not connected. */
    val mode: ShizukuMode?
        get() = (_state.value as? ShizukuState.Connected)?.mode

    private fun requireService(): IShellService =
        service ?: throw IllegalStateException("Shizuku не подключён")

    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        requireService().execCommand(command)
    }

    /** Long-running command (dexopt, compile, fstrim). [timeoutSeconds] is clamped to 1..600 by the service. */
    suspend fun execWithTimeout(command: String, timeoutSeconds: Int): String = withContext(Dispatchers.IO) {
        requireService().execCommandWithTimeout(command, timeoutSeconds)
    }

    val isConnected: Boolean
        get() = _state.value is ShizukuState.Connected && service != null

    suspend fun listDirectory(path: String): List<String> = withContext(Dispatchers.IO) {
        requireService().listDirectory(path) ?: emptyList()
    }

    suspend fun deletePath(path: String): Boolean = withContext(Dispatchers.IO) {
        requireService().deletePath(path)
    }

    /**
     * Runs a command and returns exit code, stdout and stderr separately.
     * Never throws for shell errors; throws only when Shizuku is not connected.
     */
    suspend fun execResult(command: String, timeoutSeconds: Int = 60): ShellResult = withContext(Dispatchers.IO) {
        requireService().execResult(command, timeoutSeconds)
    }

    /**
     * Streams output of a long command line by line (terminal, logcat).
     * Cancelling the collecting coroutine closes the pipe, which kills the command.
     * The last line is "${ShellMarkers.EXIT_CODE} N".
     */
    fun stream(command: String): Flow<String> = flow {
        val pfd: ParcelFileDescriptor = requireService().execStream(command)
            ?: throw IllegalStateException("Пустая команда")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { reader ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = reader.readLine() ?: break
                emit(line)
            }
        }
    }.flowOn(Dispatchers.IO)
}
