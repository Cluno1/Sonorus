package io.github.cluno1.sonorus.features.catalog.di

import android.content.Context
import io.github.cluno1.sonorus.features.catalog.data.CatalogAnnouncementReadStore
import io.github.cluno1.sonorus.features.catalog.data.CatalogRepositoryImpl
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAnnouncementsController
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAnnouncementsRepository
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRepository
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomsRepository

object CatalogModule {
    @Volatile private var repository: CatalogRepository? = null
    @Volatile private var announcements: CatalogAnnouncementsController? = null

    fun repository(context: Context): CatalogRepository = repository ?: synchronized(this) {
        repository ?: CatalogRepositoryImpl(context.applicationContext).also { repository = it }
    }

    /** issue 124: rooms share the repository's device session and error mapping. */
    fun rooms(context: Context): CatalogRoomsRepository = repository(context) as CatalogRoomsRepository

    /** issue 154: announcements share the same device session and error mapping. */
    fun announcementsRepository(context: Context): CatalogAnnouncementsRepository =
        repository(context) as CatalogAnnouncementsRepository

    /** One list per process, so the home entry and the announcement screens never disagree. */
    fun announcements(context: Context): CatalogAnnouncementsController =
        announcements ?: synchronized(this) {
            announcements ?: CatalogAnnouncementsController(
                repository = announcementsRepository(context),
                persistence = CatalogAnnouncementReadStore(context),
                serverUrl = { repository(context).connection().serverUrl },
            ).also { announcements = it }
        }
}
