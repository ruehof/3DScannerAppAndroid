package com.example.scanner3d.data.repository

import com.example.scanner3d.data.remote.StepperCallback
import com.example.scanner3d.data.remote.StepperMotorClient
import com.example.scanner3d.domain.model.ConnectionState
import com.example.scanner3d.domain.repository.TurntableRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementiert [TurntableRepository] mit dem [StepperMotorClient].
 *
 * Brückt das Callback-basierte OkHttp-WebSocket-API zu Kotlin Coroutinen
 * via [CompletableDeferred].
 *
 * @Singleton: Es darf nur eine WebSocket-Verbindung gleichzeitig existieren
 *             (ESP8266 erlaubt nur 1 Client).
 */
@Singleton
class TurntableRepositoryImpl @Inject constructor() : TurntableRepository {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // CompletableDeferred für ausstehende moveMotor-Anfragen
    private var pendingMoveDeferred: CompletableDeferred<Result<Float>>? = null

    private val client = StepperMotorClient(object : StepperCallback {

        override fun onConnected() {
            _connectionState.value = ConnectionState.Connected
        }

        override fun onDisconnected(reason: String) {
            _connectionState.value = ConnectionState.Disconnected
            // Offene Anfragen mit Fehler abschließen
            pendingMoveDeferred?.complete(Result.failure(Exception("Verbindung getrennt: $reason")))
            pendingMoveDeferred = null
        }

        override fun onMovementComplete(degreesMoved: Float) {
            pendingMoveDeferred?.complete(Result.success(degreesMoved))
            pendingMoveDeferred = null
        }

        override fun onStatusReceived(motorStatus: String, positionDegrees: Float) {
            // Status-Updates werden derzeit nicht weitergeleitet (nur für Debug)
        }

        override fun onWarning(message: String) {
            // Warnungen ignorieren (Motor läuft trotzdem)
        }

        override fun onError(message: String) {
            pendingMoveDeferred?.complete(Result.failure(Exception(message)))
            pendingMoveDeferred = null
        }

        override fun onConnectionError(error: Throwable) {
            _connectionState.value = ConnectionState.Error(error.message ?: "Verbindungsfehler")
            pendingMoveDeferred?.complete(Result.failure(error))
            pendingMoveDeferred = null
        }
    })

    override fun connect(ipAddress: String) {
        _connectionState.value = ConnectionState.Connecting
        client.connect(ipAddress)
    }

    override fun disconnect() {
        client.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Sendet einen Bewegungsbefehl und wartet suspendierend auf die Fertigstellung.
     * Thread-sicher: nutzt CompletableDeferred als Brücke zwischen Callback und Coroutine.
     */
    override suspend fun moveMotor(degrees: Float, speedDps: Float): Result<Float> {
        val deferred = CompletableDeferred<Result<Float>>()
        pendingMoveDeferred = deferred
        client.moveMotor(degrees, speedDps)
        return deferred.await()
    }
}
