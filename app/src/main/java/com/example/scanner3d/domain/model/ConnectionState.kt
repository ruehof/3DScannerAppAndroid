package com.example.scanner3d.domain.model

/**
 * Status der WebSocket-Verbindung zum ESP8266.
 * Keine Android-Abhängigkeiten.
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
