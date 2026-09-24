package io.github.cluno1.sonorus.features.clientimages.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ClientImageTransferBatchEntity::class, ClientImageTransferItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ClientImageTransferDatabase : RoomDatabase() {
    abstract fun transfers(): ClientImageTransferDao

    companion object {
        @Volatile private var instance: ClientImageTransferDatabase? = null

        fun get(context: Context): ClientImageTransferDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ClientImageTransferDatabase::class.java,
                "client_image_transfers_v1.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE client_image_transfer_items ADD COLUMN thumbnailPreparedPath TEXT")
                db.execSQL("ALTER TABLE client_image_transfer_items ADD COLUMN thumbnailMediaType TEXT")
                db.execSQL("ALTER TABLE client_image_transfer_items ADD COLUMN thumbnailByteSize INTEGER")
                db.execSQL("ALTER TABLE client_image_transfer_items ADD COLUMN thumbnailWidth INTEGER")
                db.execSQL("ALTER TABLE client_image_transfer_items ADD COLUMN thumbnailHeight INTEGER")
                db.execSQL("ALTER TABLE client_image_transfer_items ADD COLUMN thumbnailContentMd5 TEXT")
                db.execSQL("ALTER TABLE client_image_transfer_items ADD COLUMN thumbnailSha256 TEXT")
            }
        }
    }
}
