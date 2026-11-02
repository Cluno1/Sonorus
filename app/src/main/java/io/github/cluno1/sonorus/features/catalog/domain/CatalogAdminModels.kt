package io.github.cluno1.sonorus.features.catalog.domain

data class CatalogAdminDevice(
    val deviceId: String,
    val userId: String,
    val displayName: String?,
    val applicationId: String,
    val status: String,
    val isAdministrator: Boolean,
    val createdAt: String,
    val lastSeenAt: String?,
)

data class CatalogAdminDashboard(
    val devices: List<CatalogAdminDevice>,
)
