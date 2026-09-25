package io.github.cluno1.sonorus.core

import io.github.cluno1.sonorus.BuildConfig

/** Product-wide capability boundary for the customized Catalog client. */
object ProductCapabilities {
    val catalogOnly: Boolean
        get() = BuildConfig.CATALOG_ONLY

    val thirdPartyMusicServices: Boolean
        get() = !catalogOnly

    val lanSubsonicOnly: Boolean
        get() = BuildConfig.LAN_SUBSONIC_ONLY

    /** Any provider-backed playback shell may be entered. */
    val streamingMode: Boolean
        get() = allowsStreamingMode(thirdPartyMusicServices, lanSubsonicOnly)

    /** Subsonic is the only provider admitted by the private LAN build. */
    val subsonicMusic: Boolean
        get() = thirdPartyMusicServices || lanSubsonicOnly

    val jellyfinMusic: Boolean
        get() = thirdPartyMusicServices

    /** Search, playlists, mutations, downloads, recommendations, and enrichments. */
    val richStreamingFeatures: Boolean
        get() = thirdPartyMusicServices

    val firstPartyUpdates: Boolean
        get() = BuildConfig.FIRST_PARTY_UPDATES

    val inAppUpdates: Boolean
        get() = firstPartyUpdates

    val devicePublicMetadata: Boolean
        get() = BuildConfig.DEVICE_PUBLIC_METADATA

    val metadataNetworkClient: Boolean
        get() = shouldInitializeMetadataNetworkClient(thirdPartyMusicServices, devicePublicMetadata)

    internal fun shouldInitializeMetadataNetworkClient(
        thirdPartyMusicServices: Boolean,
        devicePublicMetadata: Boolean
    ): Boolean = thirdPartyMusicServices || devicePublicMetadata

    internal fun allowsStreamingMode(
        thirdPartyMusicServices: Boolean,
        lanSubsonicOnly: Boolean,
    ): Boolean = thirdPartyMusicServices || lanSubsonicOnly
}
