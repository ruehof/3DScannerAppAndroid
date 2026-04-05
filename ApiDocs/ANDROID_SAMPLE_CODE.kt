package com.example.steppercontrol

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

// ---------------------------------------------------------------------------
// Typsichere Response-Darstellung
// ---------------------------------------------------------------------------

sealed class StepperResponse {
    /** Bewegung erfolgreich abgeschlossen. */
    data class MovementOk(
        val degreesMoved: Float,
        val warning: String? = null
    ) : StepperResponse()

    /** Status-Antwort vom ESP8266. */
    data class StatusOk(
        val motorStatus: String,   // "idle" oder "moving"
        val positionDegrees: Float
    ) : StepperResponse()

    /** Fehler-Antwort vom ESP8266 (z.B. "Motor busy"). */
    data class Error(val message: String) : StepperResponse()

    /** Unbekanntes oder nicht parsebares JSON. */
    data class ParseError(val rawJson: String) : StepperResponse()
}

// ---------------------------------------------------------------------------
// Callback-Interface für den Aufrufer
// ---------------------------------------------------------------------------

interface StepperCallback {
    /** WebSocket-Verbindung erfolgreich hergestellt. */
    fun onConnected()

    /** Verbindung getrennt (normal oder durch Watchdog). */
    fun onDisconnected(reason: String)

    /** Bewegungsbefehl vom ESP8266 als abgeschlossen bestätigt. */
    fun onMovementComplete(degreesMoved: Float)

    /** Status-Antwort vom ESP8266 empfangen. */
    fun onStatusReceived(motorStatus: String, positionDegrees: Float)

    /**
     * Optionale Warnung vom ESP8266 (z.B. Speed wurde geclippt).
     * Die Bewegung wurde trotzdem ausgeführt.
     */
    fun onWarning(message: String)

    /** Fehler-Response vom ESP8266 (z.B. "Motor busy"). */
    fun onError(message: String)

    /** Verbindungsfehler auf Netzwerk-/OkHttp-Ebene. */
    fun onConnectionError(error: Throwable)
}

// ---------------------------------------------------------------------------
// Hauptklasse: StepperMotorClient
// ---------------------------------------------------------------------------

/**
 * WebSocket-Client für die ESP8266 Schrittmotor-API.
 *
 * Verbindet sich mit dem ESP8266-WebSocket-Server (Port 81, URI /ws),
 * sendet JSON-Befehle und ruft [StepperCallback]-Methoden auf dem Main Thread auf.
 *
 * Features:
 * - Automatisches Ping alle 3 Sekunden (verhindert 5s-Watchdog auf dem ESP8266)
 * - Reconnect mit exponentiellem Backoff (1s, 2s, 4s, max 8s)
 * - Thread-sicherer Callback-Dispatch auf den Android Main Thread
 * - Internes isMoving-Flag verhindert doppelte Befehle
 *
 * Verwendung:
 * ```kotlin
 * val client = StepperMotorClient(myCallback)
 * client.connect("192.168.1.42")
 * client.moveMotor(90.0f, 45.0f)
 * client.getStatus()
 * client.disconnect()
 * ```
 */
class StepperMotorClient(private val callback: StepperCallback) {

    // -----------------------------------------------------------------------
    // Öffentliche Properties
    // -----------------------------------------------------------------------

    /** Gibt an, ob eine WebSocket-Verbindung aktiv ist. */
    val isConnected: Boolean
        get() = webSocket != null && connected.get()

    // -----------------------------------------------------------------------
    // Private State
    // -----------------------------------------------------------------------

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
        .readTimeout(0, TimeUnit.MILLISECONDS)   // kein Read-Timeout (lange Bewegungen!)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // -----------------------------------------------------------------------
    // WebSocketListener (innere Klasse)
    // -----------------------------------------------------------------------

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
            if (connected.get()) {
                // Verbindung war aktiv und ist abgebrochen
                handleDisconnect("Verbindungsfehler: ${t.message}", scheduleReconnect = true)
                dispatchMain { callback.onConnectionError(t) }
            } else {
                // Verbindungsaufbau ist gescheitert
                handleDisconnect("Verbindungsaufbau fehlgeschlagen: ${t.message}", scheduleReconnect = true)
                dispatchMain { callback.onConnectionError(t) }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Öffentliche Methoden
    // -----------------------------------------------------------------------

    /**
     * Stellt eine WebSocket-Verbindung zum ESP8266 her.
     *
     * @param ipAddress IPv4-Adresse des ESP8266 im lokalen WLAN (z.B. "192.168.1.42")
     * @param port WebSocket-Port (Standard: 81)
     */
    fun connect(ipAddress: String, port: Int = 81) {
        currentIpAddress = ipAddress
        currentPort = port
        reconnectEnabled.set(true)
        openConnection()
    }

    /**
     * Sendet einen Bewegungsbefehl an den Schrittmotor.
     *
     * Die Antwort kommt asynchron über [StepperCallback.onMovementComplete].
     * Solange eine Bewegung läuft, werden weitere Aufrufe abgelehnt
     * ([StepperCallback.onError] mit "Motor ist noch in Bewegung").
     *
     * @param degrees Zielwinkel relativ zur aktuellen Position.
     *                Positiv = Uhrzeigersinn (CW), negativ = Gegenuhrzeigersinn (CCW).
     *                Kein Hard-Limit auf die Umdrehungszahl.
     * @param speedDps Geschwindigkeit in Grad/Sekunde (1.0 bis 79.0).
     *                 Werte außerhalb werden vom ESP8266 automatisch geclippt.
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

    /**
     * Fragt den aktuellen Motorstatus und die akkumulierte Position ab.
     *
     * Die Antwort kommt asynchron über [StepperCallback.onStatusReceived].
     */
    fun getStatus() {
        if (!isConnected) {
            dispatchMain { callback.onError("Nicht verbunden") }
            return
        }
        val json = JSONObject().apply {
            put("command", "status")
        }.toString()

        val sent = webSocket?.send(json) ?: false
        if (!sent) {
            dispatchMain { callback.onError("Senden fehlgeschlagen – Verbindung prüfen") }
        }
    }

    /**
     * Trennt die WebSocket-Verbindung sauber und deaktiviert automatischen Reconnect.
     *
     * Sollte in Activity.onDestroy() oder Fragment.onDestroyView() aufgerufen werden,
     * damit der ESP8266 den Motor per Watchdog stoppen kann.
     */
    fun disconnect() {
        reconnectEnabled.set(false)
        reconnectHandler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "Client getrennt")
        webSocket = null
        connected.set(false)
        isMoving.set(false)
    }

    // -----------------------------------------------------------------------
    // Private Hilfsmethoden
    // -----------------------------------------------------------------------

    private fun openConnection() {
        val url = "ws://$currentIpAddress:$currentPort/ws"
        val request = Request.Builder()
            .url(url)
            .build()
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

    /**
     * Exponentieller Backoff für Reconnect-Versuche.
     * Wartezeiten: 1s, 2s, 4s, danach immer 8s.
     */
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

    /**
     * Parst eine JSON-Response vom ESP8266 in ein typsicheres [StepperResponse]-Objekt.
     *
     * Erwartete Response-Formate:
     * - Bewegung OK:  {"status": "ok", "degrees_moved": 90.0}
     * - Bewegung OK + Warnung: {"status": "ok", "degrees_moved": 90.0, "warning": "..."}
     * - Fehler:       {"status": "error", "message": "Motor busy"}
     * - Status idle:  {"status": "idle", "position": 270.0}
     * - Status moving:{"status": "moving", "position": 270.0}
     */
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
                    val message = obj.optString("message", "Unbekannter Fehler")
                    StepperResponse.Error(message)
                }
                else -> StepperResponse.ParseError(json)
            }
        } catch (e: Exception) {
            StepperResponse.ParseError(json)
        }
    }

    /**
     * Führt einen Runnable auf dem Android Main Thread aus.
     * Thread-sicher: kann von jedem Thread aufgerufen werden.
     */
    private fun dispatchMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }
}

// ---------------------------------------------------------------------------
// Beispiel-Verwendung (nicht zur Compilation vorgesehen, nur zur Illustration)
// ---------------------------------------------------------------------------

/*

// In einer Activity oder einem Fragment:

class MotorActivity : AppCompatActivity() {

    private lateinit var stepperClient: StepperMotorClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        stepperClient = StepperMotorClient(object : StepperCallback {
            override fun onConnected() {
                statusText.text = "Verbunden"
                connectButton.isEnabled = false
                moveButton.isEnabled = true
            }
            override fun onDisconnected(reason: String) {
                statusText.text = "Getrennt: $reason"
                connectButton.isEnabled = true
                moveButton.isEnabled = false
            }
            override fun onMovementComplete(degreesMoved: Float) {
                statusText.text = "Bewegt: ${degreesMoved}°"
                moveButton.isEnabled = true
            }
            override fun onStatusReceived(motorStatus: String, positionDegrees: Float) {
                statusText.text = "Status: $motorStatus | Position: ${positionDegrees}°"
            }
            override fun onWarning(message: String) {
                Toast.makeText(this@MotorActivity, "Warnung: $message", Toast.LENGTH_SHORT).show()
            }
            override fun onError(message: String) {
                statusText.text = "Fehler: $message"
                moveButton.isEnabled = true
            }
            override fun onConnectionError(error: Throwable) {
                statusText.text = "Verbindungsfehler: ${error.message}"
            }
        })

        connectButton.setOnClickListener {
            stepperClient.connect("192.168.1.42")
        }

        moveButton.setOnClickListener {
            moveButton.isEnabled = false   // Button sperren bis Bewegung fertig
            stepperClient.moveMotor(90.0f, 45.0f)
        }

        statusButton.setOnClickListener {
            stepperClient.getStatus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stepperClient.disconnect()  // Motor-Watchdog auslösen, Ressourcen freigeben
    }
}

*/
