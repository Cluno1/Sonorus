/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.streaming.domain.model

import java.io.IOException

data class LanMusicUploadCapabilities(
    val gatewayId: String,
    val maxBytes: Long,
    val extensions: Set<String>,
)

data class LanMusicUploadResult(val trackId: String, val duplicate: Boolean)

enum class LanMusicUploadError {
    UNAVAILABLE, READ_FILE, UNSUPPORTED_FORMAT, INVALID_AUDIO, INVALID_REQUEST, TOO_LARGE,
    CONNECTION_CHANGED, LIBRARY_FULL, BUSY, STORAGE, REQUEST_FAILED,
}

/** Keep authenticated request URLs and provider exceptions out of UI errors. */
class LanMusicUploadException(val reason: LanMusicUploadError) : IOException("Music upload failed: $reason")
