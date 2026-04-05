package com.example.scanner3d.domain.repository

import com.example.scanner3d.domain.model.ScanSettings
import kotlinx.coroutines.flow.Flow

/**
 * Repository-Interface für persistente Scan-Einstellungen.
 * Keine Android-Abhängigkeiten.
 */
interface ScanSettingsRepository {
    /** Aktuelle Einstellungen als Flow (emittiert bei Änderungen) */
    val settings: Flow<ScanSettings>

    /** Einstellungen dauerhaft speichern */
    suspend fun updateSettings(settings: ScanSettings)
}
