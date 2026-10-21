package com.ultracore.app.tweaks

import android.content.Context
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Original values saved before the FIRST change of each setting,
 * so «Сбросить» returns exactly what the phone had. Stored in filesDir/tweak_backup.json.
 * A value of [UNSET] means the setting did not exist and must be deleted on reset.
 */
class TweakBackup(context: Context) {

    private val file = File(context.applicationContext.filesDir, "tweak_backup.json")
    private val json = Json { prettyPrint = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    @Synchronized
    fun has(target: SettingTarget): Boolean = target.id in read()

    @Synchronized
    fun get(target: SettingTarget): String? = read()[target.id]

    @Synchronized
    fun saveIfAbsent(target: SettingTarget, original: String?) {
        val map = read()
        if (target.id in map) return
        write(map + (target.id to (original ?: UNSET)))
    }

    @Synchronized
    fun remove(target: SettingTarget) {
        write(read() - target.id)
    }

    private fun read(): Map<String, String> = runCatching {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyMap()
    }.getOrDefault(emptyMap())

    private fun write(map: Map<String, String>) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(serializer, map))
            tmp.renameTo(file)
        }
    }

    companion object {
        const val UNSET = "<unset>"
    }
}
