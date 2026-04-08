package com.example.scanner3d.domain.model

/**
 * Einstellungen für den 3D-Scan-Prozess.
 * Keine Android-Abhängigkeiten (reine Kotlin-Klasse).
 */
data class ScanSettings(
    /** IP-Adresse des ESP8266 im lokalen WLAN */
    val espIpAddress: String = "192.168.1.100",
    /** Drehgeschwindigkeit des Motors in Grad/Sekunde (1.0 – 79.0) */
    val motorSpeedDps: Float = 45f,
    /** Pause nach jeder Bewegung in Millisekunden, damit das Objekt sich beruhigen kann */
    val pauseAfterMoveMs: Long = 1000L,
    /** Anzahl der Fotos pro Scan-Durchlauf */
    val numPhotos: Int = 10,
    /** Gesamtdrehwinkel des Drehtellers in Grad */
    val totalDegrees: Float = 360f,
    /** Auslösegeräusch bei jeder Aufnahme abspielen */
    val shutterSoundEnabled: Boolean = true
)
