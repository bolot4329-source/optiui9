package com.ultracore.app.shizuku

import android.os.Parcel
import android.os.Parcelable

/**
 * Result of one shell command run by [ShellUserService].
 * Declared for AIDL in app/src/main/aidl/com/ultracore/app/shizuku/ShellResult.aidl.
 */
class ShellResult(
    /** Process exit code; [CODE_TIMEOUT] / [CODE_LAUNCH_ERROR] for our own failures. */
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    /** Output was longer than the Binder-safe limit and was cut. */
    val truncated: Boolean,
    val timedOut: Boolean,
) : Parcelable {

    val isSuccess: Boolean get() = exitCode == 0 && !timedOut

    /** stdout + stderr, for showing to the user. */
    val combined: String
        get() = listOf(stdout.trim(), stderr.trim()).filter { it.isNotEmpty() }.joinToString("\n")

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(exitCode)
        dest.writeString(stdout)
        dest.writeString(stderr)
        dest.writeInt(if (truncated) 1 else 0)
        dest.writeInt(if (timedOut) 1 else 0)
    }

    override fun describeContents(): Int = 0

    override fun toString(): String =
        "ShellResult(exitCode=$exitCode, timedOut=$timedOut, truncated=$truncated)"

    companion object {
        const val CODE_TIMEOUT = -1
        const val CODE_LAUNCH_ERROR = -2

        @JvmField
        val CREATOR: Parcelable.Creator<ShellResult> = object : Parcelable.Creator<ShellResult> {
            override fun createFromParcel(source: Parcel): ShellResult = ShellResult(
                exitCode = source.readInt(),
                stdout = source.readString().orEmpty(),
                stderr = source.readString().orEmpty(),
                truncated = source.readInt() == 1,
                timedOut = source.readInt() == 1,
            )

            override fun newArray(size: Int): Array<ShellResult?> = arrayOfNulls(size)
        }
    }
}
