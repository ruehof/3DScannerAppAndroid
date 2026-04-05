package com.example.scanner3d.data.remote

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * WebSocket-Client für die ESP8266 Schrittmotor-API.
 *
 * Verbindet sich mit ws://<ip>:81/ws, sendet JSON-Befehle und ruft
 * [StepperCallback]-Methoden auf dem Android Main Thread auf.
 *
 * Features:
 * - Automatischer Ping alle 3 Sekunden (verhindert 5s-Watchdog auf dem ESP8266)
 * - Reconnect mit exponentiellem Backoff (1s, 2s, 4s, max 8s)
 * - Thread-sicherer Callback-Dispatch auf den Main Thread
 * - isMoving-Flag verhindert doppelte Motor-Befehle
 */
class StepperMotorClient(private val callback: StepperCallback) {

    val isConnected: Boolean
        get() = webSocket != null && connected.get()

    private var webSocket: WebSocket? = null
    private var currentIpAddress: String = ""
    private var currentPort: Int = 81

    private val connected = AtomicBoolean(false)
    private val isMoving = AtomicBoolean(false)
    private val reconnectEnabled = AtomicBoolean(false)
    private val reconnectAttempt = AtomicInteger(0)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val reconnectHandler = Handler(Looper.getMainLooper())

    // OkHttpClient mit 3s Ping-Intervall (ESP8266 Watchdog = 5s)
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(3, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)  // kein Read-Timeout für lange Bewegungen
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private inner class StepperWebSocketListener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected.set(true)
            reconnectAttempt.set(0)
            dispatchMain { callback.onConnected() }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val response = parseResponse(text)
            dispatchMain {
                when (response) {
                    is StepperResponse.MovementOk -> {
                        isMoving.set(false)
                        response.warning?.let { callback.onWarning(it) }
                        callback.onMovementComplete(response.degreesMoved)
                    }
                    is StepperResponse.StatusOk -> {
                        callback.onStatusReceived(response.motorStatus, response.positionDegrees)
                    }
                    is StepperResponse.Error -> {
                        isMoving.set(false)
                        callback.onError(response.message)
                    }
                    is StepperResponse.ParseError -> {
                        callback.onError("Unbekannte Antwort: ${response.rawJson}")
                    }
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleDisconnect("Verbindung getrennt: $reason (Code $code)", scheduleReconnect = false)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handleDisconnect(
                "Verbindungsfehler: ${t.message}",
                scheduleReconnect = true
            )
            dispatchMain { callback.onConnectionError(t) }
        }
    }

    fun connect(ipAddress: String, port: Int = 81) {
        currentIpAddress = ipAddress
        currentPort = port
        reconnectEnabled.set(true)
        openConnection()
    }

    /**
     * Motor bewegen. Positiv = Uhrzeigersinn, negativ = Gegenuhrzeigersinn.
     * @param degrees Zielwinkel relativ zur aktuellen Position
     * @param speedDps Geschwindigkeit in °/s (1.0 – 79.0, wird vom ESP8266 geclippt)
     */
    fun moveMotor(degrees: Float, speedDps: Float) {
        if (!isConnected) {
            dispatchMain { callback.onError("Nicht verbunden") }
            return
        }
        if (isMoving.get()) {
            dispatchMain { callback.onError("Motor ist noch in Bewegung") }
            return
        }
        val json = JSONObject().apply {
            put("degrees", degrees.toDouble())
            put("speed", speedDps.toDouble())
        }.toString()

        isMoving.set(true)
        val sent = webSocket?.send(json) ?: false
        if (!sent) {
            isMoving.set(false)
            dispatchMain { callback.onError("Senden fehlgeschlagen – Verbindung prüfen") }
        }
    }

    fun getStatus() {
        if (!isConnected) {
            dispatchMain { callback.onError("Nicht verbunden") }
            return
        }
        val json = JSONObject().apply {
            put("command", "status")
        }.toString()
        webSocket?.send(json) ?: dispatchMain { callback.onError("Senden fehlgeschlagen") }
    }

    fun disconnect() {
        reconnectEnabled.set(false)
        reconnectHandler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "Client getrennt")
        webSocket = null
        connected.set(false)
        isMoving.set(false)
    }

    private fun openConnection() {
        val url = "ws://$currentIpAddress:$currentPort/ws"
        val request = Request.Builder().url(url).build()
        webSocket = httpClient.newWebSocket(request, StepperWebSocketListener())
    }

    private fun handleDisconnect(reason: String, scheduleReconnect: Boolean) {
        connected.set(false)
        isMoving.set(false)
        webSocket = null
        dispatchMain { callback.onDisconnected(reason) }
        if (scheduleReconnect && reconnectEnabled.get()) {
            scheduleReconnect()
        }
    }

    /** Exponentieller Backoff: 1s, 2s, 4s, dann immer 8s */
    private fun scheduleReconnect() {
        val attempt = reconnectAttempt.getAndIncrement()
        val delaySeconds = when {
            attempt == 0 -> 1L
            attempt == 1 -> 2L
            attempt == 2 -> 4L
            else -> 8L
        }
        reconnectHandler.postDelayed({
            if (reconnectEnabled.get() && !connected.get()) {
                openConnection()
            }
        }, delaySeconds * 1000L)
    }

    private fun parseResponse(json: String): StepperResponse {
        return try {
            val obj = JSONObject(json)
            val status = obj.optString("status", "")
            when (status) {
                "ok" -> {
                    val degreesMoved = obj.optDouble("degrees_moved", 0.0).toFloat()
                    val warning = if (obj.has("warning")) obj.getString("warning") else null
                    StepperResponse.MovementOk(degreesMoved, warning)
                }
                "idle", "moving" -> {
                    val position = obj.optDouble("position", 0.0).toFloat()
                    StepperResponse.StatusOk(status, position)
                }
                "error" -> {
                    StepperResponse.Error(obj.optString("message", "Unbekannter Fehler"))
                }
                else -> StepperResponse.ParseError(json)
            }
        } catch (e: Exception) {
            StepperResponse.ParseError(json)
        }
    }

    private fun dispatchMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }
}
