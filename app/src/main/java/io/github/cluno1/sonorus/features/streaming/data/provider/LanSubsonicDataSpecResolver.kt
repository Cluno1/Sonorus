/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.features.streaming.data.provider

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import io.github.cluno1.sonorus.core.ProductCapabilities
import io.github.cluno1.sonorus.features.streaming.di.StreamingMusicModule
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSubsonicPlaybackPolicy
import kotlinx.coroutines.runBlocking

/** Exchanges a stable LAN queue identity for a fresh authenticated stream URL at open time. */
object LanSubsonicDataSpecResolver {
    fun resolve(context: Context, dataSpec: DataSpec): DataSpec {
        if (!LanSubsonicPlaybackPolicy.allowsDataSpec(
                enabled = ProductCapabilities.lanSubsonicOnly,
                uri = dataSpec.uri.toString(),
                customCacheKey = dataSpec.key,
            )
        ) {
            return dataSpec
        }

        val mediaId = LanSubsonicPlaybackPolicy.mediaIdFromPlaybackUri(dataSpec.uri.toString())
            ?: return dataSpec
        val repository = StreamingMusicModule.provideStreamingMusicRepository(context)
        val freshUrl = runBlocking { repository.getStreamingUrl(mediaId) }
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return dataSpec
        return dataSpec.withUri(freshUrl.toUri())
    }
}
