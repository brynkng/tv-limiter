package dev.tvlimiter.data

import android.content.Context
import dev.tvlimiter.domain.BlockCacheStore
import dev.tvlimiter.domain.CachedBlock
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class BlockCacheFile(val blocks: List<CachedBlock> = emptyList())

/** The block cache in tubeguard-blocks.json. It is read once and kept in memory, so checking a playing video touches no disk. */
class BlockCacheRepository(private val store: JsonFileStore<BlockCacheFile>) : BlockCacheStore {
    private val lock = Any()
    private var cached: List<CachedBlock>? = null

    override fun load(): List<CachedBlock> = synchronized(lock) { cached ?: store.read().blocks.also { cached = it } }

    override fun save(entries: List<CachedBlock>) = synchronized(lock) {
        cached = entries
        try {
            store.write(BlockCacheFile(entries))
        } catch (e: java.io.IOException) {
            // The cache only speeds up a block. Keeping it in memory is enough until the next write works.
        }
    }

    companion object {
        @Volatile private var instance: BlockCacheRepository? = null

        fun get(context: Context): BlockCacheRepository = instance ?: synchronized(this) {
            instance ?: BlockCacheRepository(
                JsonFileStore(File(context.applicationContext.filesDir, "tubeguard-blocks.json"), BlockCacheFile.serializer()) { BlockCacheFile() },
            ).also { instance = it }
        }
    }
}
