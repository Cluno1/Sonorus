package io.github.cluno1.sonorus.features.local.data.model

import androidx.annotation.StringRes
import io.github.cluno1.sonorus.R

/** issue 159: what a toggle really changed, so a prompt never reports a change that did not happen. */
enum class FavoriteChange {
    ADDED,
    REMOVED,
    UNCHANGED,
    ;

    @get:StringRes
    val messageRes: Int?
        get() = when (this) {
            ADDED -> R.string.player_favorite_added_message
            REMOVED -> R.string.player_favorite_removed_message
            UNCHANGED -> null
        }

    companion object {
        fun between(wasFavorite: Boolean, isFavorite: Boolean): FavoriteChange = when {
            wasFavorite == isFavorite -> UNCHANGED
            isFavorite -> ADDED
            else -> REMOVED
        }
    }
}
