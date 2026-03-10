package com.unclebike.meshride.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.unclebike.meshride.ui.QuickMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "meshride_prefs")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_PAIRED_DEVICE_ADDRESS = stringPreferencesKey("paired_device_address")
        private val KEY_PAIRED_DEVICE_NAME = stringPreferencesKey("paired_device_name")
        private val KEY_QUICK_MESSAGES = stringPreferencesKey("quick_messages")
        private val KEY_USER_ALIAS = stringPreferencesKey("user_alias")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val pairedDeviceAddress: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PAIRED_DEVICE_ADDRESS]
    }

    val pairedDeviceName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PAIRED_DEVICE_NAME]
    }

    val quickMessages: Flow<List<QuickMessage>> = context.dataStore.data.map { prefs ->
        val stored = prefs[KEY_QUICK_MESSAGES]
        if (stored != null) {
            try {
                json.decodeFromString<List<QuickMessageDto>>(stored).map {
                    QuickMessage(slot = it.slot, label = it.label, text = it.text)
                }
            } catch (e: Exception) {
                defaultQuickMessages()
            }
        } else {
            defaultQuickMessages()
        }
    }

    suspend fun setPairedDevice(address: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PAIRED_DEVICE_ADDRESS] = address
            prefs[KEY_PAIRED_DEVICE_NAME] = name
        }
    }

    suspend fun clearPairedDevice() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_PAIRED_DEVICE_ADDRESS)
            prefs.remove(KEY_PAIRED_DEVICE_NAME)
        }
    }

    suspend fun setQuickMessages(messages: List<QuickMessage>) {
        val dtos = messages.map { QuickMessageDto(it.slot, it.label, it.text) }
        context.dataStore.edit { prefs ->
            prefs[KEY_QUICK_MESSAGES] = json.encodeToString(dtos)
        }
    }

    private fun defaultQuickMessages() = listOf(
        QuickMessage(slot = 0, label = "Msg 1", text = "Regroup!"),
        QuickMessage(slot = 1, label = "Msg 2", text = "Flat tire"),
        QuickMessage(slot = 2, label = "Msg 3", text = "Stopping ahead"),
    )
}

@Serializable
private data class QuickMessageDto(
    val slot: Int,
    val label: String,
    val text: String,
)
