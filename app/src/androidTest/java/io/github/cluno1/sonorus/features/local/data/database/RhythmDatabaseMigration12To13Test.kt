/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.features.local.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RhythmDatabaseMigration12To13Test {
    @Test
    fun migrationCreatesPlaylistSnapshotsAndBackfillsExistingSongs() {
        val renditionId = "123e4567-e89b-42d3-a456-426614174000"
        val assetId = "123e4567-e89b-42d3-a456-426614174001"
        val legacyCatalogId = "rhythm-catalog:rendition:$renditionId:asset:$assetId"
        val stableCatalogId = "rhythm-catalog:rendition:$renditionId"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                }).build(),
        )
        helper.writableDatabase.use { db ->
            db.execSQL(
                """
                CREATE TABLE songs (
                    id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL,
                    album TEXT NOT NULL, albumId TEXT NOT NULL, duration INTEGER NOT NULL,
                    uri TEXT NOT NULL, artworkUri TEXT, trackNumber INTEGER NOT NULL,
                    year INTEGER NOT NULL, genre TEXT, dateAdded INTEGER NOT NULL,
                    dateModified INTEGER NOT NULL, albumArtist TEXT, bitrate INTEGER,
                    sampleRate INTEGER, channels INTEGER, codec TEXT,
                    discNumber INTEGER NOT NULL, path TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE playlist_songs (
                    playlistId TEXT NOT NULL, songId TEXT NOT NULL, orderIndex INTEGER NOT NULL,
                    PRIMARY KEY(playlistId, songId)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO songs VALUES (
                    'SUBSONIC::track-42', 'LAN song', 'Artist', 'Album', 'album-1', 123000,
                    'streaming://track/SUBSONIC%3A%3Atrack-42',
                    'http://192.168.1.10/rest/getCoverArt?t=secret&s=salt', 1, 2026, NULL,
                    1000, 1000, NULL, NULL, NULL, NULL, 'audio/mpeg', 1, NULL
                )
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO playlist_songs VALUES ('playlist-1', 'SUBSONIC::track-42', 0)")
            db.execSQL(
                """
                INSERT INTO songs VALUES (
                    '$legacyCatalogId', 'Catalog song', 'Artist', 'Catalog album', 'album-2', 456000,
                    'https://expired.example/audio.mp3?signature=temporary',
                    'https://expired.example/cover.jpg?signature=temporary', 2, 2025, NULL,
                    2000, 2000, NULL, NULL, NULL, NULL, 'audio/flac', 1, NULL
                )
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO playlist_songs VALUES ('playlist-1', '$legacyCatalogId', 1)")

            RhythmDatabase.MIGRATION_12_13.migrate(db)

            db.query(
                "SELECT source, title, uri, artworkUri FROM playlist_song_snapshots " +
                    "WHERE playlistId = 'playlist-1' AND songId = 'SUBSONIC::track-42'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("LAN_SUBSONIC", cursor.getString(0))
                assertEquals("LAN song", cursor.getString(1))
                assertEquals("streaming://track/SUBSONIC%3A%3Atrack-42", cursor.getString(2))
                assertTrue(cursor.isNull(3))
            }
            db.query("SELECT COUNT(*) FROM songs WHERE id = 'SUBSONIC::track-42'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query(
                "SELECT source, uri, artworkUri FROM playlist_song_snapshots " +
                    "WHERE playlistId = 'playlist-1' AND songId = '$stableCatalogId'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("CATALOG", cursor.getString(0))
                assertEquals("rhythm-catalog://rendition/$renditionId", cursor.getString(1))
                assertTrue(cursor.isNull(2))
            }
            db.query(
                "SELECT songId FROM playlist_songs WHERE playlistId = 'playlist-1' " +
                    "ORDER BY orderIndex",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("SUBSONIC::track-42", cursor.getString(0))
                assertTrue(cursor.moveToNext())
                assertEquals(stableCatalogId, cursor.getString(0))
            }
        }
        helper.close()
    }
}
