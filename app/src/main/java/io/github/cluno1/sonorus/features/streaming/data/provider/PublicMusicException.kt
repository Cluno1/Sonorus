package io.github.cluno1.sonorus.features.streaming.data.provider

import android.content.Context
import androidx.annotation.StringRes
import io.github.cluno1.sonorus.R
import java.io.IOException

/** Structured public music failure. The data layer stays free of user-facing text; see [publicMusicErrorMessage]. */
class PublicMusicException(val kind: Kind, val httpCode: Int? = null) :
    IOException("PublicMusicException kind=$kind http=$httpCode") {
    enum class Kind(@param:StringRes val messageRes: Int) {
        DEVICE_NOT_REGISTERED(R.string.public_music_error_not_registered),
        SERVER_MISMATCH(R.string.public_music_error_server_mismatch),
        SERVICE_UNAVAILABLE(R.string.public_music_error_service_unavailable),
        INVALID_SONG_ID(R.string.public_music_error_invalid_song),
        SEARCH_FAILED(R.string.public_music_error_search),
        SOURCES_FAILED(R.string.public_music_error_sources),
        METADATA_READ_FAILED(R.string.public_music_error_metadata_read),
        METADATA_SAVE_FAILED(R.string.public_music_error_metadata_save),
        MANUAL_SAVE_FAILED(R.string.public_music_error_manual_save),
    }
}

/** Localized text for any failure raised while talking to public music; other errors keep their own message. */
fun Context.publicMusicErrorMessage(failure: Throwable): String = when {
    failure is PublicMusicException -> getString(failure.kind.messageRes, failure.httpCode ?: 0)
    else -> failure.message ?: getString(R.string.public_music_error_unknown)
}
