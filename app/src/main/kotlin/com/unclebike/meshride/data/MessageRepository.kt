package com.unclebike.meshride.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
) {
    val recentMessages: Flow<List<MeshPacket>> = messageDao.getRecentMessages()

    suspend fun saveMessage(message: MeshPacket) {
        messageDao.insert(message)
        messageDao.pruneOldMessages()
    }
}
