package com.example.scanner3d.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanner3d.domain.model.ConnectionState
import com.example.scanner3d.domain.repository.ScanSettingsRepository
import com.example.scanner3d.domain.repository.TurntableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Status des laufenden Scan-Vorgangs */
sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(val current: Int, val total: Int) : ScanState()
    data class Complete(val photoCount: Int) : ScanState()
    data class Error(val message: String) : ScanState()
}

/** UI-Zustand des Kamera-Screens */
data class CameraUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val scanState: ScanState = ScanState.Idle,
    val numPhotos: Int = 10,
    val totalDegrees: Float = 360f,
    val capturedPhotoCount: Int = 0,
    val errorMessage: String? = null,
    val espIpAddress: String = "192.168.1.100"
) {
    /** Berechnet den Drehwinkel pro Schritt */
    val stepDegrees: Float get() = if (numPhotos > 0) totalDegrees / numPhotos else 0f
    val isScanning: Boolean get() = scanState is ScanState.Scanning
}

/**
 * ViewModel für den Kamera-Screen.
 *
 * Orchestriert den 3D-Scan-Ablauf:
 * 1. Foto aufnehmen (via takePicture-Lambda von der UI)
 * 2. Drehteller um stepDegrees bewegen
 * 3. Pause abwarten (Objekt beruhigen)
 * 4. Wiederholen bis alle Fotos aufgenommen
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val turntableRepository: TurntableRepository,
    private val scanSettingsRepository: ScanSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        // Verbindungsstatus aus dem Repository beobachten
        viewModelScope.launch {
            turntableRepository.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                // Wenn Verbindung während Scan verloren geht, Scan abbrechen
                if (state is ConnectionState.Error || state is ConnectionState.Disconnected) {
                    if (_uiState.value.isScanning) {
                        cancelScan()
                    }
                }
            }
        }
        // Gespeicherte IP aus Einstellungen laden
        viewModelScope.launch {
            scanSettingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(espIpAddress = settings.espIpAddress) }
            }
        }
    }

    /** Verbindung zum ESP8266 herstellen */
    fun connect() {
        turntableRepository.connect(_uiState.value.espIpAddress)
    }

    /** Verbindung zum ESP8266 trennen */
    fun disconnect() {
        turntableRepository.disconnect()
    }

    fun setNumPhotos(n: Int) {
        if (n in 1..360 && !_uiState.value.isScanning) {
            _uiState.update { it.copy(numPhotos = n) }
        }
    }

    fun setTotalDegrees(d: Float) {
        if (d in 1f..360f && !_uiState.value.isScanning) {
            _uiState.update { it.copy(totalDegrees = d) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun resetScan() {
        if (!_uiState.value.isScanning) {
            _uiState.update { it.copy(scanState = ScanState.Idle, capturedPhotoCount = 0) }
        }
    }

    /**
     * Scan-Ablauf starten.
     *
     * @param takePicture Lambda-Funktion aus dem Kamera-Screen, die ein Foto aufnimmt
     *                    und den gespeicherten URI zurückgibt. Wird auf dem Main-Thread
     *                    aufgerufen (CameraX-Executor).
     */
    fun startScan(takePicture: suspend (index: Int) -> Result<Uri>) {
        if (_uiState.value.isScanning) return
        if (_uiState.value.connectionState !is ConnectionState.Connected) {
            _uiState.update { it.copy(errorMessage = "Nicht mit Drehteller verbunden. Bitte erst verbinden.") }
            return
        }

        val numPhotos = _uiState.value.numPhotos
        val stepDegrees = _uiState.value.stepDegrees

        scanJob = viewModelScope.launch {
            val settings = scanSettingsRepository.settings.first()
            _uiState.update { it.copy(
                scanState = ScanState.Scanning(0, numPhotos),
                capturedPhotoCount = 0,
                errorMessage = null
            )}

            for (i in 0 until numPhotos) {
                // Status aktualisieren
                _uiState.update { it.copy(scanState = ScanState.Scanning(i + 1, numPhotos)) }

                // Foto aufnehmen
                val photoResult = takePicture(i + 1)
                if (photoResult.isFailure) {
                    _uiState.update { it.copy(
                        scanState = ScanState.Error("Foto ${i + 1} fehlgeschlagen: ${photoResult.exceptionOrNull()?.message}")
                    )}
                    return@launch
                }

                _uiState.update { it.copy(capturedPhotoCount = i + 1) }

                // Motor drehen (außer nach dem letzten Foto)
                if (i < numPhotos - 1) {
                    val moveResult = turntableRepository.moveMotor(
                        degrees = stepDegrees,
                        speedDps = settings.motorSpeedDps
                    )
                    if (moveResult.isFailure) {
                        _uiState.update { it.copy(
                            scanState = ScanState.Error("Motorfehler bei Schritt ${i + 1}: ${moveResult.exceptionOrNull()?.message}")
                        )}
                        return@launch
                    }

                    // Pause nach Bewegung – Objekt beruhigen lassen
                    delay(settings.pauseAfterMoveMs)
                }
            }

            _uiState.update { it.copy(scanState = ScanState.Complete(numPhotos)) }
        }
    }

    /** Laufenden Scan abbrechen */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(scanState = ScanState.Idle) }
    }

    override fun onCleared() {
        super.onCleared()
        turntableRepository.disconnect()
    }
}
