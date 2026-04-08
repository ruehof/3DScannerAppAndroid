package com.example.scanner3d.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanner3d.domain.model.ScanSettings
import com.example.scanner3d.domain.repository.ScanSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: ScanSettings = ScanSettings(
        espIpAddress = "192.168.1.100",
        motorSpeedDps = 30f,
        pauseAfterMoveMs = 1000L
    ),
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false
)

/**
 * ViewModel für den Einstellungen-Screen.
 * Liest/schreibt Einstellungen via DataStore.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val scanSettingsRepository: ScanSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            scanSettingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun updateIpAddress(ip: String) {
        _uiState.update { it.copy(settings = it.settings.copy(espIpAddress = ip)) }
    }

    fun updateMotorSpeed(speed: Float) {
        val clipped = speed.coerceIn(1f, 79f)
        _uiState.update { it.copy(settings = it.settings.copy(motorSpeedDps = clipped)) }
    }

    fun updatePauseAfterMove(ms: Long) {
        val clipped = ms.coerceIn(0L, 10000L)
        _uiState.update { it.copy(settings = it.settings.copy(pauseAfterMoveMs = clipped)) }
    }

    fun updateNumPhotos(n: Int) {
        val clipped = n.coerceIn(4, 72)
        _uiState.update { it.copy(settings = it.settings.copy(numPhotos = clipped)) }
    }

    fun updateTotalDegrees(d: Float) {
        val clipped = d.coerceIn(90f, 360f)
        _uiState.update { it.copy(settings = it.settings.copy(totalDegrees = clipped)) }
    }

    fun updateShutterSoundEnabled(enabled: Boolean) {
        _uiState.update { it.copy(settings = it.settings.copy(shutterSoundEnabled = enabled)) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, savedSuccessfully = false) }
            scanSettingsRepository.updateSettings(_uiState.value.settings)
            _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
        }
    }

    fun clearSavedFlag() {
        _uiState.update { it.copy(savedSuccessfully = false) }
    }
}
