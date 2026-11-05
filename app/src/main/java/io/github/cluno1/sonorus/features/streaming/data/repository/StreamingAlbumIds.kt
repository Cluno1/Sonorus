package io.github.cluno1.sonorus.features.streaming.data.repository

/**
 * issue 157: a song and the album built from it must carry the same ID. Public music songs have no
 * provider album ID; left null, the library mapping invented another format and every such album
 * ended up with no songs.
 */
internal object StreamingAlbumIds {
    fun fromProviderId(serviceId: String, providerAlbumId: String): String =
        "$serviceId::album::${providerAlbumId.replace("/", "_slash_")}"

    fun fromNames(serviceId: String, artist: String, album: String): String =
        "$serviceId::album::${normalize(artist)}::${normalize(album)}"

    /** Null only for a song without an album name, which belongs to no album. */
    fun forSong(serviceId: String, providerAlbumId: String?, artist: String, album: String): String? = when {
        !providerAlbumId.isNullOrBlank() -> fromProviderId(serviceId, providerAlbumId)
        album.isNotBlank() -> fromNames(serviceId, artist, album)
        else -> null
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace("\\s+".toRegex(), "_")
}
