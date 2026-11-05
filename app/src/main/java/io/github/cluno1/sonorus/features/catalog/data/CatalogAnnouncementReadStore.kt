package io.github.cluno1.sonorus.features.catalog.data

import android.content.Context
import io.github.cluno1.sonorus.features.catalog.domain.AnnouncementReadPersistence
import io.github.cluno1.sonorus.features.catalog.domain.AnnouncementReadState

/** issue 154: IDs are stored in order so the oldest can be dropped; server IDs contain no newline. */
internal class CatalogAnnouncementReadStore(context: Context) : AnnouncementReadPersistence {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): AnnouncementReadState? {
        if (!preferences.contains(KEY_BASELINE)) return null
        return AnnouncementReadState(
            baselineEpochMillis = preferences.getLong(KEY_BASELINE, 0L),
            readIds = ids(KEY_READ),
            dismissedIds = ids(KEY_DISMISSED),
        )
    }

    override fun save(state: AnnouncementReadState) {
        preferences.edit()
            .putLong(KEY_BASELINE, state.baselineEpochMillis)
            .putString(KEY_READ, state.readIds.joinToString("\n"))
            .putString(KEY_DISMISSED, state.dismissedIds.joinToString("\n"))
            .apply()
    }

    private fun ids(key: String): List<String> =
        preferences.getString(key, null).orEmpty().split('\n').filter(String::isNotEmpty)

    private companion object {
        const val FILE = "catalog_announcements"
        const val KEY_BASELINE = "baseline_epoch_millis"
        const val KEY_READ = "read_ids"
        const val KEY_DISMISSED = "dismissed_ids"
    }
}
