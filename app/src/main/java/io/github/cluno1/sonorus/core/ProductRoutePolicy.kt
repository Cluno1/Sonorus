package io.github.cluno1.sonorus.core

/** Keeps legacy navigation hand-offs from reopening capabilities removed by Catalog-only builds. */
object ProductRoutePolicy {
    private val catalogOnlySettingsRoutes = setOf(
        "go_settings",
        "api_management_settings",
    )

    fun allowsInitialNavigationRoute(
        route: String?,
        catalogOnly: Boolean,
        lanSubsonicOnly: Boolean = false,
    ): Boolean {
        if (route.isNullOrBlank()) return false
        if (!catalogOnly) return true
        if (!route.startsWith("streaming_", ignoreCase = true)) return true
        return lanSubsonicOnly && route.equals(
            "streaming_service_setup/SUBSONIC",
            ignoreCase = true,
        )
    }

    fun allowsSettingsSubroute(
        route: String?,
        catalogOnly: Boolean,
        lanSubsonicOnly: Boolean = false,
    ): Boolean {
        if (route.isNullOrBlank()) return false
        if (!catalogOnly) return true
        if (lanSubsonicOnly && route == "lan_subsonic_settings") return true
        return route !in catalogOnlySettingsRoutes
    }
}
