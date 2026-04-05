package com.example.scanner3d.domain.repository

import com.example.scanner3d.domain.model.ConnectionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository-Interface für die Steuerung des Drehtellers via ESP8266 WebSocket.
 * Keine Android-Abhängigkeiten – reines Kotlin-Interface.
 */
interface TurntableRepository {

    /** Aktueller Verbindungsstatus als StateFlow */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Verbindung zum ESP8266 herstellen.
     * @param ipAddress IP-Adresse des ESP8266 im lokalen WLAN
     */
    fun connect(ipAddress: String)

    /** Verbindung zum ESP8266 trennen */
    fun disconnect()

    /**
     * Motor um den angegebenen Winkel drehen (suspendierend – wartet auf Fertigstellung).
     * @param degrees Winkel in Grad (positiv = Uhrzeigersinn, negativ = Gegenuhrzeigersinn)
     * @param speedDps Geschwindigkeit in Grad/Sekunde (wird auf [1.0, 79.0] geclippt)
     * @return Result mit dem tatsächlich zurückgelegten Winkel oder Fehler
     */
    suspend fun moveMotor(degrees: Float, speedDps: Float): Result<Float>
}
