package io.github.cluno1.sonorus.features.clientimages.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ClientImageTransferBatchEntity::class, ClientImageTransferItemEntity::class],
    version = 1,
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
            ).build().also { instance = it }
        }
    }
}
