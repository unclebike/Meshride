package com.unclebike.meshride.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Database(entities = [MeshPacket::class], version = 1, exportSchema = false)
abstract class MeshRideDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 20")
    fun getRecentMessages(): Flow<List<MeshPacket>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MeshPacket)

    @Query("DELETE FROM messages WHERE id NOT IN (SELECT id FROM messages ORDER BY timestamp DESC LIMIT 20)")
    suspend fun pruneOldMessages()

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int
}
