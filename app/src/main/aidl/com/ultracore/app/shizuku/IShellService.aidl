// Privileged shell bridge. Implemented by ShellUserService, which Shizuku runs as UID 2000 (shell).
// IMPORTANT: when changing this interface, bump versionCode in app/build.gradle.kts,
// otherwise Shizuku keeps the old UserService process with the old interface.
package com.ultracore.app.shizuku;

interface IShellService {

    // Runs `sh -c <command>` with the default 60 s timeout, returns stdout+stderr.
    String execCommand(String command) = 1;

    // Lists a directory. Directories end with "/". Works for Android/data and Android/obb.
    List<String> listDirectory(String path) = 2;

    // Recursively deletes a path inside /storage/emulated/0 (protected top-level folders are refused).
    boolean deletePath(String path) = 3;

    // Same as execCommand, but with a custom timeout (1..600 s) for long optimization jobs.
    String execCommandWithTimeout(String command, int timeoutSeconds) = 4;

    // Transaction code reserved by Shizuku for destroying the UserService process.
    void destroy() = 16777114;
}
