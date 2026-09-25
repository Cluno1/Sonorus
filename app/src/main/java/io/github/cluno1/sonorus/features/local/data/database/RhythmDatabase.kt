/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.features.local.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.cluno1.sonorus.features.local.data.database.dao.ArtistDao
import io.github.cluno1.sonorus.features.local.data.database.dao.PlaylistDao
import io.github.cluno1.sonorus.features.local.data.database.dao.SongArtistDao
import io.github.cluno1.sonorus.features.local.data.database.dao.SongDao
import io.github.cluno1.sonorus.features.local.data.database.dao.DeviceMetadataDao
import io.github.cluno1.sonorus.features.local.data.database.dao.DeviceAlbumMetadataDao
import io.github.cluno1.sonorus.features.local.data.database.entity.ArtistEntity
import io.github.cluno1.sonorus.features.local.data.database.entity.PlaylistEntity
import io.github.cluno1.sonorus.features.local.data.database.entity.PlaylistSongEntity
import io.github.cluno1.sonorus.features.local.data.database.entity.PlaylistSongSnapshotEntity
import io.github.cluno1.sonorus.features.local.data.database.entity.SongArtistEntity
import io.github.cluno1.sonorus.features.local.data.database.entity.SongEntity
import io.github.cluno1.sonorus.features.local.data.database.entity.DeviceMetadataEntity
import io.github.cluno1.sonorus.features.local.data.database.entity.DeviceAlbumMetadataEntity
import io.github.cluno1.sonorus.features.local.data.database.entity.DeviceSongAlbumEntity

@Database(entities = [SongEntity::class, ArtistEntity::class, SongArtistEntity::class, PlaylistEntity::class, PlaylistSongEntity::class, PlaylistSongSnapshotEntity::class, DeviceMetadataEntity::class, DeviceAlbumMetadataEntity::class, DeviceSongAlbumEntity::class], version = 13, exportSchema = false)
abstract class RhythmDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun artistDao(): ArtistDao
    abstract fun songArtistDao(): SongArtistDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun deviceMetadataDao(): DeviceMetadataDao
    abstract fun deviceAlbumMetadataDao(): DeviceAlbumMetadataDao

    companion object {
        @Volatile
        private var INSTANCE: RhythmDatabase? = null

        // Migration from version 1 to 2: Add artist and song-artist tables
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create artists table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `artists` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `artworkUri` TEXT, 
                        `numberOfAlbums` INTEGER NOT NULL, 
                        `numberOfTracks` INTEGER NOT NULL, 
                        `groupByAlbumArtist` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """)

                // Create song_artists table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `song_artists` (
                        `songId` TEXT NOT NULL, 
                        `artistName` TEXT NOT NULL, 
                        `groupByAlbumArtist` INTEGER NOT NULL, 
                        PRIMARY KEY(`songId`, `artistName`, `groupByAlbumArtist`)
                    )
                """)
            }
        }

        // Migration from version 2 to 3: No schema changes, just version bump
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes needed, just ensure tables exist
            }
        }

        // Migration from version 3 to 4: Switch from destructive to proper migrations
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes, just preserve existing data
            }
        }

        // Migration from version 4 to 5: Persist multi-disc ordering metadata.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN discNumber INTEGER NOT NULL DEFAULT 1")
            }
        }

        // Migration from version 5 to 6: Persist song-level modified timestamp.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN dateModified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE songs SET dateModified = dateAdded WHERE dateModified = 0")
            }
        }

        // Migration from version 6 to 7: Add path column to songs table
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN path TEXT")
            }
        }

        // Migration from version 7 to 8: Add playlists and playlist_songs tables
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `dateCreated` INTEGER NOT NULL, 
                        `dateModified` INTEGER NOT NULL, 
                        `artworkUri` TEXT, 
                        PRIMARY KEY(`id`)
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlist_songs` (
                        `playlistId` TEXT NOT NULL, 
                        `songId` TEXT NOT NULL, 
                        `orderIndex` INTEGER NOT NULL, 
                        PRIMARY KEY(`playlistId`, `songId`)
                    )
                """)
            }
        }

        // Migration from version 8 to 9: Fix dateAdded and dateModified timestamps stored in seconds instead of milliseconds
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE songs SET dateAdded = dateAdded * 1000 WHERE dateAdded > 0 AND dateAdded < 100000000000")
                db.execSQL("UPDATE songs SET dateModified = dateModified * 1000 WHERE dateModified > 0 AND dateModified < 100000000000")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `device_metadata` (
                        `stableId` TEXT NOT NULL, `songId` TEXT NOT NULL, `contentUri` TEXT NOT NULL,
                        `fingerprint` TEXT NOT NULL, `titleSource` TEXT NOT NULL, `artistSource` TEXT NOT NULL,
                        `albumSource` TEXT NOT NULL, `lyricsSource` TEXT, `lyricsProvider` TEXT,
                        `lyricsExternalId` TEXT, `lyricsConfidence` REAL, `lyricsPlain` TEXT,
                        `lyricsSynced` TEXT, `lyricsCachePath` TEXT, `lyricsPinned` INTEGER NOT NULL, `artworkSource` TEXT,
                        `artworkProvider` TEXT, `artworkExternalId` TEXT, `artworkConfidence` REAL,
                        `artworkCachePath` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`stableId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_device_metadata_songId` ON `device_metadata` (`songId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_device_metadata_fingerprint` ON `device_metadata` (`fingerprint`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `device_album_metadata` (
                        `albumKey` TEXT NOT NULL, `localTitle` TEXT NOT NULL, `localArtist` TEXT NOT NULL,
                        `provider` TEXT, `externalReleaseId` TEXT, `externalReleaseGroupId` TEXT,
                        `confidence` REAL, `pinned` INTEGER NOT NULL, `artworkSource` TEXT,
                        `artworkCachePath` TEXT, `artworkSha256` TEXT, `mediaType` TEXT, `byteSize` INTEGER,
                        `matchedAt` INTEGER, `updatedAt` INTEGER NOT NULL, `negativeUntil` INTEGER NOT NULL,
                        PRIMARY KEY(`albumKey`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `device_song_album` (
                        `songStableId` TEXT NOT NULL, `albumKey` TEXT NOT NULL,
                        PRIMARY KEY(`songStableId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_device_song_album_albumKey` ON `device_song_album` (`albumKey`)")

                // Preserve v10 public/folder covers as one album record. Legacy fallback keys
                // deliberately remain DEVICE-only and are reconciled on the next media scan.
                db.execSQL("""
                    INSERT OR IGNORE INTO `device_album_metadata` (
                        `albumKey`, `localTitle`, `localArtist`, `provider`, `externalReleaseId`,
                        `externalReleaseGroupId`, `confidence`, `pinned`, `artworkSource`,
                        `artworkCachePath`, `artworkSha256`, `mediaType`, `byteSize`, `matchedAt`,
                        `updatedAt`, `negativeUntil`
                    )
                    SELECT
                        CASE WHEN s.albumId IS NOT NULL AND s.albumId != '' AND s.albumId != '0' AND s.albumId != '-1'
                            THEN 'mediastore:external:' || s.albumId ELSE 'legacy:' || dm.stableId END,
                        COALESCE(s.album, ''), COALESCE(NULLIF(s.albumArtist, ''), s.artist, ''),
                        dm.artworkProvider, dm.artworkExternalId, NULL, dm.artworkConfidence, 0,
                        dm.artworkSource, dm.artworkCachePath, NULL, NULL, NULL,
                        CASE WHEN dm.artworkProvider IS NOT NULL THEN dm.updatedAt ELSE NULL END,
                        dm.updatedAt, 0
                    FROM device_metadata dm LEFT JOIN songs s ON s.id = dm.songId
                    WHERE dm.artworkCachePath IS NOT NULL
                    ORDER BY CASE dm.artworkSource WHEN 'USER_SELECTED' THEN 4 WHEN 'EMBEDDED' THEN 3 WHEN 'SIBLING' THEN 2 ELSE 1 END DESC,
                             dm.updatedAt DESC
                """.trimIndent())
                db.execSQL("""
                    INSERT OR REPLACE INTO `device_song_album` (`songStableId`, `albumKey`)
                    SELECT dm.stableId,
                        CASE WHEN s.albumId IS NOT NULL AND s.albumId != '' AND s.albumId != '0' AND s.albumId != '-1'
                            THEN 'mediastore:external:' || s.albumId ELSE 'legacy:' || dm.stableId END
                    FROM device_metadata dm LEFT JOIN songs s ON s.id = dm.songId
                """.trimIndent())
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE device_metadata ADD COLUMN detailsProvider TEXT")
                db.execSQL("ALTER TABLE device_metadata ADD COLUMN detailsExternalId TEXT")
                db.execSQL("ALTER TABLE device_metadata ADD COLUMN detailsConfidence REAL")
                db.execSQL("ALTER TABLE device_metadata ADD COLUMN detailsPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE device_metadata ADD COLUMN titleOverride TEXT")
                db.execSQL("ALTER TABLE device_metadata ADD COLUMN artistOverride TEXT")
                db.execSQL("ALTER TABLE device_metadata ADD COLUMN albumOverride TEXT")
                db.execSQL("ALTER TABLE device_metadata ADD COLUMN albumArtistOverride TEXT")
                db.execSQL("ALTER TABLE device_metadata ADD COLUMN yearOverride INTEGER")
                db.execSQL("ALTER TABLE device_metadata ADD COLUMN trackNumberOverride INTEGER")
                db.execSQL("ALTER TABLE device_metadata ADD COLUMN discNumberOverride INTEGER")
                db.execSQL("ALTER TABLE device_metadata ADD COLUMN genreOverride TEXT")
            }
        }

        /** Keep remote playlist snapshots independent from the MediaStore-backed songs table. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `playlist_song_snapshots` (
                        `playlistId` TEXT NOT NULL,
                        `songId` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `albumId` TEXT NOT NULL,
                        `duration` INTEGER NOT NULL,
                        `uri` TEXT NOT NULL,
                        `artworkUri` TEXT,
                        `trackNumber` INTEGER NOT NULL,
                        `year` INTEGER NOT NULL,
                        `genre` TEXT,
                        `dateAdded` INTEGER NOT NULL,
                        `dateModified` INTEGER NOT NULL,
                        `albumArtist` TEXT,
                        `bitrate` INTEGER,
                        `sampleRate` INTEGER,
                        `channels` INTEGER,
                        `codec` TEXT,
                        `discNumber` INTEGER NOT NULL,
                        `path` TEXT,
                        PRIMARY KEY(`playlistId`, `songId`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR REPLACE INTO `playlist_song_snapshots` (
                        `playlistId`, `songId`, `source`, `title`, `artist`, `album`, `albumId`,
                        `duration`, `uri`, `artworkUri`, `trackNumber`, `year`, `genre`,
                        `dateAdded`, `dateModified`, `albumArtist`, `bitrate`, `sampleRate`,
                        `channels`, `codec`, `discNumber`, `path`
                    )
                    SELECT ps.`playlistId`,
                        CASE
                            WHEN s.`id` LIKE 'rhythm-catalog:rendition:%'
                                THEN 'rhythm-catalog:rendition:' || substr(s.`id`, 26, 36)
                            ELSE s.`id`
                        END,
                        CASE
                            WHEN s.`id` LIKE 'rhythm-catalog:rendition:%' THEN 'CATALOG'
                            WHEN s.`id` LIKE 'SUBSONIC::%' THEN 'LAN_SUBSONIC'
                            ELSE 'DEVICE_OR_LEGACY'
                        END,
                        s.`title`, s.`artist`, s.`album`, s.`albumId`, s.`duration`,
                        CASE
                            WHEN s.`id` LIKE 'rhythm-catalog:rendition:%'
                                THEN 'rhythm-catalog://rendition/' || substr(s.`id`, 26, 36)
                            WHEN s.`id` LIKE 'SUBSONIC::%'
                                THEN 'streaming://track/' || replace(s.`id`, ':', '%3A')
                            ELSE s.`uri`
                        END,
                        CASE
                            WHEN s.`id` LIKE 'SUBSONIC::%' THEN NULL
                            WHEN s.`id` LIKE 'rhythm-catalog:rendition:%'
                                AND s.`artworkUri` NOT LIKE 'rhythm-catalog://asset/%' THEN NULL
                            ELSE s.`artworkUri`
                        END,
                        s.`trackNumber`, s.`year`, s.`genre`, s.`dateAdded`,
                        s.`dateModified`, s.`albumArtist`, s.`bitrate`, s.`sampleRate`,
                        s.`channels`, s.`codec`, s.`discNumber`, s.`path`
                    FROM `playlist_songs` ps
                    INNER JOIN `songs` s ON s.`id` = ps.`songId`
                """.trimIndent())
                db.execSQL("""
                    UPDATE OR REPLACE `playlist_songs`
                    SET `songId` = 'rhythm-catalog:rendition:' || substr(`songId`, 26, 36)
                    WHERE `songId` LIKE 'rhythm-catalog:rendition:%'
                """.trimIndent())
                db.execSQL("DELETE FROM `songs` WHERE `id` LIKE 'rhythm-catalog:rendition:%'")
                db.execSQL("DELETE FROM `songs` WHERE `id` LIKE 'SUBSONIC::%'")
            }
        }

        fun getInstance(context: Context): RhythmDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RhythmDatabase::class.java,
                    "rhythm_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
