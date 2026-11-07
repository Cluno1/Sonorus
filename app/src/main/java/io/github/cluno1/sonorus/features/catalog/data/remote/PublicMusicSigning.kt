package io.github.cluno1.sonorus.features.catalog.data.remote

/**
 * Public music writes a device may sign, the same three routes the public gateway allows
 * (backend ``public_main.py``): a song's metadata, a song's lyrics (issue 166) and lyrics shared
 * for a local song (issue 166).
 *
 * Invariant: every PUT the app sends to these APIs is listed here. issue 176: issue 166 added the two
 * lyrics writes without listing them, so the first lyrics save threw inside the OkHttp interceptor
 * and crashed the app.
 */
private val PUBLIC_MUSIC_WRITES = listOf(
    Regex("^/v2/music/sources/[a-z][a-z0-9_-]{1,39}/songs/[A-Za-z0-9_-]{1,80}/(?:metadata|lyrics)$"),
    Regex("^/v2/music/device-lyrics$"),
)

internal fun isPublicMusicWrite(method: String, encodedPath: String): Boolean =
    method == "PUT" && PUBLIC_MUSIC_WRITES.any { it.matches(encodedPath) }
