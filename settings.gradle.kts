pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Shizuku 13.1.5 is published on Maven Central, so JitPack is not needed.
        // Uncomment if a future dependency lives only on JitPack:
        // maven("https://jitpack.io")
    }
}

rootProject.name = "UltraCore"
include(":app")
