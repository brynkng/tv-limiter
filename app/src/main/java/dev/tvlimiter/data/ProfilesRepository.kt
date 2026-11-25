package dev.tvlimiter.data

import android.content.Context
import dev.tvlimiter.domain.AgeBand
import dev.tvlimiter.domain.Limit
import dev.tvlimiter.domain.Mode
import dev.tvlimiter.domain.Profile
import dev.tvlimiter.domain.Profiles
import dev.tvlimiter.domain.ProfilesFile
import dev.tvlimiter.domain.Viewer
import java.io.File

/**
 * The mode and the list of child profiles, in profiles.json. A TV without the file is in
 * shared mode with no profiles, which is how every install before profiles behaves.
 */
class ProfilesRepository(private val store: JsonFileStore<ProfilesFile>) {
    private val lock = Any()
    private var cached: ProfilesFile? = null

    fun current(): ProfilesFile = synchronized(lock) { cached ?: store.read().also { cached = it } }

    fun mode(): Mode = current().mode

    fun profiles(): List<Profile> = current().profiles

    fun find(id: String): Profile? = current().find(id)

    fun setMode(mode: Mode) = synchronized(lock) { save(current().copy(mode = mode)) }

    fun upsert(profile: Profile) = synchronized(lock) { save(current().withProfile(profile)) }

    fun remove(id: String) = synchronized(lock) { save(current().without(id)) }

    private fun save(file: ProfilesFile) {
        store.write(file)
        cached = file
    }

    companion object {
        @Volatile private var instance: ProfilesRepository? = null

        fun get(context: Context): ProfilesRepository = instance ?: synchronized(this) {
            instance ?: ProfilesRepository(
                JsonFileStore(File(context.applicationContext.filesDir, "profiles.json"), ProfilesFile.serializer()) {
                    ProfilesFile()
                },
            ).also { instance = it }
        }

        /** Adds a child profile whose limits start as a copy of the shared limits. */
        fun add(context: Context, name: String, band: AgeBand): Profile {
            val repo = get(context)
            val profile = Profile(Profiles.newId(repo.profiles().map { it.id }.toSet()), name, band)
            val shared: List<Limit> = LimitsRepository.get(context).all()
            LimitsRepository.get(context, profile.id).replaceAll(shared)
            repo.upsert(profile)
            return profile
        }

        /** Deletes a child profile with its limits, usage, history and passkey. */
        fun delete(context: Context, id: String) {
            if (id == Profiles.SHARED_ID) return
            // Stop counting for this profile first, so the tracking service doesn't write its files again.
            if (Viewer.shared.current == id) Viewer.shared.end()
            get(context).remove(id)
            LimitsRepository.forget(id)
            UsageRepository.forget(id)
            HistoryRepository.forget(id)
            PinRepository.forget(id)
            Profiles.folder(context.applicationContext.filesDir, id).deleteRecursively()
        }
    }
}
