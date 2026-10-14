package com.ultracore.app.shizuku

sealed interface ShizukuState {
    /** Shizuku service is not running (or not installed). */
    data object NotRunning : ShizukuState

    /** Shizuku is running, but UltraCore has no permission yet. */
    data object PermissionDenied : ShizukuState

    /** Permission granted, binding ShellUserService. */
    data object Connecting : ShizukuState

    /** ShellUserService is bound. [uidInfo] is the output of `id` inside the service. */
    data class Connected(val uidInfo: String) : ShizukuState

    /** Something went wrong (old Shizuku, bind timeout, exception). */
    data class Error(val message: String) : ShizukuState
}
