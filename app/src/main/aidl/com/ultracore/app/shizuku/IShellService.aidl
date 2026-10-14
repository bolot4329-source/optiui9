// Privileged shell bridge. Implemented by ShellUserService, which Shizuku runs as UID 2000 (shell).
package com.ultracore.app.shizuku;

interface IShellService {

    // Runs `sh -c <command>`, returns stdout+stderr (with timeout).
    String execCommand(String command) = 1;

    // Lists a directory. Directories end with "/". Works for Android/data and Android/obb.
    List<String> listDirectory(String path) = 2;

    // Recursively deletes a path inside /storage/emulated/0 (protected top-level folders are refused).
    boolean deletePath(String path) = 3;

    // Transaction code reserved by Shizuku for destroying the UserService process.
    void destroy() = 16777114;
}
