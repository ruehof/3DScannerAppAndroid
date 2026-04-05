package com.example.scanner3d.data.remote

/**
 * Typsichere Darstellung der JSON-Responses vom ESP8266.
 */
sealed class StepperResponse {
    /** Bewegung erfolgreich abgeschlossen */
    data class MovementOk(
        val degreesMoved: Float,
        val warning: String? = null
    ) : StepperResponse()

    /** Status-Antwort vom ESP8266 */
    data class StatusOk(
        val motorStatus: String,  // "idle" oder "moving"
        val positionDegrees: Float
    ) : StepperResponse()

    /** Fehler-Antwort (z.B. "Motor busy") */
    data class Error(val message: String) : StepperResponse()

    /** Unbekanntes oder nicht parsebares JSON */
    data class ParseError(val rawJson: String) : StepperResponse()
}
