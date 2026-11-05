package io.github.cluno1.sonorus.features.streaming.domain

/** issue 158: home carousel order and the album IDs of recommended playlists. */
internal object DiscoverCarousel {
    fun <T> interleave(groups: List<List<T>>): List<T> {
        val longest = groups.maxOfOrNull { it.size } ?: 0
        return (0 until longest).flatMap { index -> groups.mapNotNull { it.getOrNull(index) } }
    }

    fun <T> pick(groups: List<List<T>>, count: Int): List<T> =
        interleave(groups.map { it.shuffled() }).take(count)
}

internal object DiscoverPlaylistIds {
    private const val PREFIX = "discover::"
    private const val SEPARATOR = "::"

    fun encode(sourceId: String, externalId: String): String = "$PREFIX$sourceId$SEPARATOR$externalId"

    fun isDiscover(id: String): Boolean = decode(id) != null

    /** Returns the source ID and the playlist ID. */
    fun decode(id: String): Pair<String, String>? {
        if (!id.startsWith(PREFIX)) return null
        val parts = id.removePrefix(PREFIX).split(SEPARATOR)
        if (parts.size != 2 || parts.any { it.isBlank() }) return null
        return parts[0] to parts[1]
    }
}
