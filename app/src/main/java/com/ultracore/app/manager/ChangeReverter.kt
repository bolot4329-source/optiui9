package com.ultracore.app.manager

import com.ultracore.app.sandbox.SandboxManager

/** Undoes everything UltraCore changed in apps: freeze, pause, removal, background limits, sandbox. */
object ChangeReverter {

    data class Result(val done: Int, val failures: List<Pair<String, String>>)

    private enum class Step(val title: String) {
        RESTORE("Восстановление"),
        UNFREEZE("Разморозка"),
        UNSUSPEND("Снятие паузы"),
        BACKGROUND("Фон"),
    }

    suspend fun revertApps(repository: AppManagerRepository, onStep: (String) -> Unit = {}): Result {
        val failures = mutableListOf<Pair<String, String>>()
        var done = 0

        // Sandbox first: waking an app also removes it from the freeze/pause lists.
        onStep("Песочница…")
        val sandboxBefore = SandboxManager.state.value.packages.size
        val sandboxFailures = SandboxManager.clearAll()
        failures += sandboxFailures
        done += (sandboxBefore - sandboxFailures.size).coerceAtLeast(0)

        // A removed app must be restored before it can be unfrozen or unpaused.
        val c = AppChangeStore.state.value
        val steps = c.removed.map { it to Step.RESTORE } +
            c.frozen.map { it to Step.UNFREEZE } +
            c.suspended.map { it to Step.UNSUSPEND } +
            c.restricted.keys.map { it to Step.BACKGROUND }

        steps.forEachIndexed { i, (pkg, step) ->
            onStep("${step.title}: ${i + 1} из ${steps.size}")
            // The app may have been uninstalled completely since — nothing to revert, just forget it.
            if (!repository.isOnPhone(pkg)) {
                AppChangeStore.update { s ->
                    s.copy(
                        frozen = s.frozen - pkg,
                        suspended = s.suspended - pkg,
                        removed = s.removed - pkg,
                        restricted = s.restricted - pkg,
                    )
                }
                return@forEachIndexed
            }
            val r = when (step) {
                Step.RESTORE -> repository.restore(pkg)
                Step.UNFREEZE -> repository.unfreeze(pkg)
                Step.UNSUSPEND -> repository.unsuspendApp(pkg)
                Step.BACKGROUND -> repository.allowBackground(pkg)
            }
            if (r.ok) done++ else failures += pkg to "${step.title}: ${r.message}"
        }
        return Result(done, failures)
    }
}
