/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.local.data.device

import io.github.cluno1.sonorus.core.ProductCapabilities
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSubsonicPlaybackPolicy
import io.github.cluno1.sonorus.shared.data.model.Song

/**
 * issue 166: songs that may open the manual lyrics search. Local songs get every manual tab; public
 * songs get lyrics only, and their pick is stored on the backend instead of this device's cache.
 */
internal fun Song.canSearchLyricsManually(): Boolean =
    DeviceMetadataPolicy.isEligible(id, uri.scheme) || isPublicMusicSong()

internal fun Song.isPublicMusicSong(): Boolean =
    LanSubsonicPlaybackPolicy.isLanSong(ProductCapabilities.lanSubsonicOnly, id, uri.toString())
