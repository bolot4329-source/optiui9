package com.ultracore.app.shizuku

/** How Shizuku itself was started: through ADB (UID 2000, no root) or with root (UID 0). */
enum class ShizukuMode(val label: String) {
    ADB("ADB, без Root"),
    ROOT("Root"),
}

sealed interface ShizukuState {
    /** The Shizuku app is not installed on this phone. */
    data object NotInstalled : ShizukuState

    /** Shizuku is installed, but its service is not running (after a reboot, for example). */
    data object NotRunning : ShizukuState

    /** Shizuku is running, but UltraCore has no permission yet. */
    data object PermissionDenied : ShizukuState

    /** Permission granted, binding ShellUserService. */
    data object Connecting : ShizukuState

    /** ShellUserService is bound. [uidInfo] is the output of `id` inside the service. */
    data class Connected(val uidInfo: String, val mode: ShizukuMode) : ShizukuState

    /** Something went wrong (old Shizuku, bind timeout, exception). */
    data class Error(val message: String) : ShizukuState
}
