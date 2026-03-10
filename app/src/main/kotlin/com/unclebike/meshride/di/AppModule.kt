package com.unclebike.meshride.di

import android.content.Context
import androidx.room.Room
import com.unclebike.meshride.ble.MeshtasticBleConnection
import com.unclebike.meshride.ble.MeshtasticConnection
import com.unclebike.meshride.data.MessageDao
import com.unclebike.meshride.data.MeshRideDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {
    @Binds
    @Singleton
    abstract fun bindMeshtasticConnection(impl: MeshtasticBleConnection): MeshtasticConnection
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeshRideDatabase {
        return Room.databaseBuilder(
            context,
            MeshRideDatabase::class.java,
            "meshride-db",
        ).build()
    }

    @Provides
    fun provideMessageDao(database: MeshRideDatabase): MessageDao {
        return database.messageDao()
    }
}
