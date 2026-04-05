package com.example.scanner3d.data.remote

/**
 * Callback-Interface für asynchrone ESP8266-WebSocket-Ereignisse.
 * Alle Methoden werden auf dem Android Main Thread aufgerufen.
 */
interface StepperCallback {
    /** WebSocket-Verbindung erfolgreich hergestellt */
    fun onConnected()

    /** Verbindung getrennt (normal oder durch ESP8266-Watchdog) */
    fun onDisconnected(reason: String)

    /** Bewegungsbefehl vollständig ausgeführt */
    fun onMovementComplete(degreesMoved: Float)

    /** Status-Antwort empfangen */
    fun onStatusReceived(motorStatus: String, positionDegrees: Float)

    /** Optionale Warnung (z.B. Speed wurde auf 79°/s geclippt) */
    fun onWarning(message: String)

    /** Fehler-Response vom ESP8266 (z.B. "Motor busy") */
    fun onError(message: String)

    /** Netzwerk-/OkHttp-Verbindungsfehler */
    fun onConnectionError(error: Throwable)
}
