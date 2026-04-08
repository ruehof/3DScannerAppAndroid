package com.example.scanner3d.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.scanner3d.domain.model.ScanSettings
import com.example.scanner3d.domain.repository.ScanSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// DataStore-Erweiterungseigenschaft auf Context-Ebene
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scan_settings")

/**
 * Persistiert die Scan-Einstellungen in DataStore (Preferences).
 * Nutzt @ApplicationContext um Memory Leaks zu vermeiden.
 */
@Singleton
class ScanSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ScanSettingsRepository {

    private object Keys {
        val ESP_IP = stringPreferencesKey("esp_ip_address")
        val MOTOR_SPEED = floatPreferencesKey("motor_speed_dps")
        val PAUSE_AFTER_MOVE = longPreferencesKey("pause_after_move_ms")
        val NUM_PHOTOS = intPreferencesKey("num_photos")
        val TOTAL_DEGREES = floatPreferencesKey("total_degrees")
        val SHUTTER_SOUND = booleanPreferencesKey("shutter_sound_enabled")
    }

    override val settings: Flow<ScanSettings> = context.dataStore.data.map { prefs ->
        ScanSettings(
            espIpAddress = prefs[Keys.ESP_IP] ?: "192.168.1.100",
            motorSpeedDps = prefs[Keys.MOTOR_SPEED] ?: 45f,
            pauseAfterMoveMs = prefs[Keys.PAUSE_AFTER_MOVE] ?: 1000L,
            numPhotos = prefs[Keys.NUM_PHOTOS] ?: 10,
            totalDegrees = prefs[Keys.TOTAL_DEGREES] ?: 360f,
            shutterSoundEnabled = prefs[Keys.SHUTTER_SOUND] ?: true
        )
    }

    override suspend fun updateSettings(settings: ScanSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ESP_IP] = settings.espIpAddress
            prefs[Keys.MOTOR_SPEED] = settings.motorSpeedDps
            prefs[Keys.PAUSE_AFTER_MOVE] = settings.pauseAfterMoveMs
            prefs[Keys.NUM_PHOTOS] = settings.numPhotos
            prefs[Keys.TOTAL_DEGREES] = settings.totalDegrees
            prefs[Keys.SHUTTER_SOUND] = settings.shutterSoundEnabled
        }
    }
}
