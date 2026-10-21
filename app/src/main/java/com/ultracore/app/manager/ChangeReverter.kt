package com.ultracore.app.manager

import com.ultracore.app.sandbox.SandboxManager

/** Undoes everything UltraCore changed in apps: freeze, pause, removal, background limits, sandbox. */
object ChangeReverter {

    data class Result(val done: Int, val failures: List<Pair<String, String>>)

    suspend fun revertApps(repository: AppManagerRepository, onStep: (String) -> Unit = {}): Result {
        val failures = mutableListOf<Pair<String, String>>()
        var done = 0

        onStep("Песочница…")
        failures += SandboxManager.clearAll()

        val c = AppChangeStore.state.value
        val steps = c.frozen.map { it to "Разморозка" } +
            c.suspended.map { it to "Снятие паузы" } +
            c.removed.map { it to "Восстановление" } +
            c.restricted.keys.map { it to "Фон" }
        steps.forEachIndexed { i, (pkg, what) ->
            onStep("$what: ${i + 1} из ${steps.size}")
            val r = when (what) {
                "Разморозка" -> repository.unfreeze(pkg)
                "Снятие паузы" -> repository.unsuspendApp(pkg)
                "Восстановление" -> repository.restore(pkg)
                else -> repository.allowBackground(pkg)
            }
            if (r.ok) {
                done++
            } else {
                failures += pkg to "$what: ${r.message}"
                // An app that is no longer on the phone cannot be reverted — forget it.
                if ("unknown package" in r.message.lowercase() || "not found" in r.message.lowercase()) {
                    AppChangeStore.update { s ->
                        s.copy(frozen = s.frozen - pkg, suspended = s.suspended - pkg, restricted = s.restricted - pkg)
                    }
                }
            }
        }
        return Result(done, failures)
    }
}
