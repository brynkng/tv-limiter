package dev.tvlimiter.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * One JSON document in one file. Writes go to a temp file that is then renamed over the
 * original, so a crash can't leave half a file. A corrupt file is moved aside to `.corrupt`
 * and the default is used, so a bad file never crashes the tracking service.
 */
class JsonFileStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
) {
    fun read(): T {
        if (!file.exists()) return default()
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (e: SerializationException) {
            moveAside()
        } catch (e: IllegalArgumentException) {
            moveAside()
        }
    }

    fun write(value: T) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        // Sync before the rename so a power cut can't leave the renamed file empty.
        FileOutputStream(temp).use { out ->
            out.write(json.encodeToString(serializer, value).toByteArray())
            out.fd.sync()
        }
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) throw IOException("Could not replace $file")
        }
    }

    private fun moveAside(): T {
        val aside = File(file.parentFile, "${file.name}.corrupt")
        aside.delete()
        file.renameTo(aside)
        return default()
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
