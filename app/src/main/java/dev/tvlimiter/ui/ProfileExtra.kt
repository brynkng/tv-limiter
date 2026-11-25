package dev.tvlimiter.ui

import android.app.Activity
import android.content.Intent
import dev.tvlimiter.domain.Profiles

/** The settings screens that show one profile's limits or history carry its id in their intent. */
internal const val EXTRA_PROFILE_ID = "profile_id"

internal fun Intent.withProfile(profileId: String): Intent = putExtra(EXTRA_PROFILE_ID, profileId)

/** The profile this screen is for; the shared profile when none was given. */
internal val Activity.profileId: String
    get() = intent.getStringExtra(EXTRA_PROFILE_ID) ?: Profiles.SHARED_ID
