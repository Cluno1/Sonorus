package io.github.cluno1.sonorus.features.local.data.model

/** Browsing joins sources by stable identity; connection state never selects a whole library. */
object UnifiedLibraryPolicy {
    fun <T> mergeById(vararg sources: List<T>, id: (T) -> String): List<T> =
        sources.asSequence().flatten().distinctBy(id).toList()

    fun isLan(mediaId: String): Boolean =
        UnifiedPlaylistPolicy.source(mediaId) == UnifiedPlaylistSource.LAN_SUBSONIC

    fun isDevice(mediaId: String): Boolean =
        UnifiedPlaylistPolicy.source(mediaId) == UnifiedPlaylistSource.DEVICE_OR_LEGACY
}
