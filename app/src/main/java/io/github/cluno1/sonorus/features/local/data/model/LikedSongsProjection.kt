package io.github.cluno1.sonorus.features.local.data.model

/**
 * issue 159: the Liked tab cannot be derived from the library alone. A public music song is not
 * part of the library, so its only copy is the snapshot kept in the Liked playlist.
 */
object LikedSongsProjection {
    fun <T> merge(
        library: List<T>,
        likedPlaylist: List<T>,
        favoriteIds: Set<String>,
        stableId: (T) -> String,
    ): List<T> = (library + likedPlaylist)
        .filter { stableId(it) in favoriteIds }
        .distinctBy(stableId)
}
