# Android API Guide: ESP8266 Schrittmotor WebSocket-API

## 1. Übersicht

Dieser Guide richtet sich an Android-Entwickler, die einen 28BYJ-48 Schrittmotor
über einen Wemos D1 Mini (ESP8266) per WebSocket aus einer Android-App steuern
wollen.

### Was die API macht

Der ESP8266 betreibt einen WebSocket-Server im lokalen WLAN. Deine Android-App
sendet JSON-Befehle mit Zielwinkel und Geschwindigkeit. Der ESP8266 fährt den
Motor und antwortet mit dem tatsächlich zurückgelegten Winkel. Ein Status-Befehl
erlaubt die Abfrage des aktuellen Zustands und der akkumulierten Position.

### Hardware-Kurzüberblick

| Komponente    | Detail                                               |
|---------------|------------------------------------------------------|
| MCU           | ESP8266 (Wemos D1 Mini), 80 MHz                      |
| Motor         | 28BYJ-48 Unipolar-Schrittmotor, Half-Step, 4096 Schritte/U |
| Treiber       | ULN2003A                                             |
| Verbindung    | 802.11 b/g/n WLAN (2.4 GHz), kein Bluetooth         |
| Server        | WebSocket auf Port 81, URI `/ws`                     |
| Max. Clients  | **1 gleichzeitiger Client**                          |

---

## 2. Verbindungsaufbau

### IP-Adresse des ESP8266 ermitteln

Der ESP8266 verbindet sich beim Start automatisch mit dem konfigurierten WLAN-Netz.
Die vergebene IP-Adresse wird über die serielle Konsole ausgegeben (115200 Baud).
Typische Wege, die IP zu finden:

1. **Router-DHCP-Tabelle**: Im Router-Admin-Interface nach dem Hostnamen `esp8266`
   oder dem MAC-Adress-Präfix `18:FE:34:...` suchen.
2. **Serielles Log**: Nach dem Flashen erscheint im Serial Monitor:
   ```
   I (xxxx) wifi_manager: IP-Adresse: 192.168.1.42
   ```
3. **mDNS** (zukünftig): In v1.0 nicht implementiert.

### WebSocket-Verbindung herstellen

```
ws://<ESP8266_IP>:81/ws
```

Beispiel: `ws://192.168.1.42:81/ws`

**Wichtig:**
- Kein TLS (kein `wss://`) – der ESP8266 unterstützt in dieser Version kein HTTPS,
  da mbedTLS ~36 KB Heap benötigen würde und der freie Heap nur ~12 KB beträgt.
- Android 9+ verbietet standardmäßig unverschlüsselte HTTP-Verbindungen.
  Du musst `usesCleartextTraffic="true"` in der `AndroidManifest.xml` setzen
  (siehe Abschnitt 8).

### Verbindungs-Handshake

Die Verbindung wird per HTTP Upgrade etabliert. Mit OkHttp geschieht das
automatisch. Eine explizite Anmeldung oder Authentifizierung ist nicht erforderlich.
Nach dem `onOpen`-Callback ist die Verbindung sofort bereit für Befehle.

---

## 3. API-Referenz

Alle Nachrichten sind UTF-8-kodierte JSON-Strings. Binäre WebSocket-Frames
werden vom ESP8266 ignoriert.

---

### 3.1 Motor bewegen

Sendet einen Bewegungsbefehl mit Zielwinkel und Geschwindigkeit.

**Request:**
```json
{"degrees": 90.0, "speed": 45.0}
```

| Feld      | Typ   | Wertebereich          | Beschreibung                                        |
|-----------|-------|-----------------------|-----------------------------------------------------|
| `degrees` | float | unbegrenzt (kein Hard-Limit) | Relativer Winkel zur aktuellen Position. Positiv = Uhrzeigersinn (CW), negativ = Gegenuhrzeigersinn (CCW). Minimalauflösung: 1° (~11 Half-Steps). |
| `speed`   | float | 1.0 – 79.0            | Geschwindigkeit in Grad/Sekunde. Werte außerhalb werden automatisch auf [1.0, 79.0] geclippt. |

**Response (Erfolg):**
```json
{"status": "ok", "degrees_moved": 90.0}
```

Die Response wird gesendet, nachdem die Bewegung vollständig abgeschlossen ist.
Bei langen Drehwinkeln (z.B. 3600°) kann das entsprechend lange dauern. Die
Verbindung bleibt während der gesamten Bewegung offen.

**Response (Speed außerhalb Bereich – mit Warnung):**

Wenn `speed` außerhalb von [1.0, 79.0] liegt, wird der Wert geclippt und die
Bewegung trotzdem ausgeführt:
```json
{"status": "ok", "degrees_moved": 90.0, "warning": "speed clipped to 79.0"}
```

**Response (Motor beschäftigt):**
```json
{"status": "error", "message": "Motor busy"}
```

Tritt auf, wenn ein neuer Befehl gesendet wird, während der Motor noch eine
vorherige Bewegung ausführt. Der neue Befehl wird verworfen.

---

### 3.2 Status abfragen

Fragt den aktuellen Zustand und die akkumulierte Position ab.

**Request:**
```json
{"command": "status"}
```

**Response:**
```json
{"status": "idle", "position": 270.0}
```

| Feld       | Typ    | Mögliche Werte        | Beschreibung                                         |
|------------|--------|-----------------------|------------------------------------------------------|
| `status`   | string | `"idle"`, `"moving"`  | Aktueller Motorstatus                                |
| `position` | float  | unbegrenzt            | Akkumulierte Position in Grad seit dem letzten Systemstart. Kann > 360 oder negativ sein. Wird bei Neustart auf 0.0 zurückgesetzt. |

---

### 3.3 Vollständige Response-Tabelle

| Situation                        | Response                                                            |
|----------------------------------|---------------------------------------------------------------------|
| Bewegung erfolgreich             | `{"status": "ok", "degrees_moved": <float>}`                       |
| Bewegung OK + Speed geclippt     | `{"status": "ok", "degrees_moved": <float>, "warning": "<text>"}` |
| Motor war beschäftigt            | `{"status": "error", "message": "Motor busy"}`                     |
| Ungültiges JSON                  | `{"status": "error", "message": "Invalid JSON"}`                   |
| Fehlende Felder im Request       | `{"status": "error", "message": "Missing fields"}`                 |
| Status-Antwort (idle)            | `{"status": "idle", "position": <float>}`                          |
| Status-Antwort (moving)          | `{"status": "moving", "position": <float>}`                        |

---

## 4. Fehlerbehandlung

### 4.1 Fehler-Responses vom ESP8266

**`Motor busy`**
- Ursache: Ein Bewegungsbefehl wurde gesendet, während der Motor noch läuft.
- Lösung: Warte auf die `{"status": "ok", ...}` Response der laufenden Bewegung,
  bevor du den nächsten Befehl sendest. Alternativ: sende zuerst einen Status-Query
  um zu prüfen, ob der Motor idle ist.

**`Invalid JSON`**
- Ursache: Das gesendete JSON ist syntaktisch fehlerhaft.
- Lösung: JSON vor dem Senden mit `JSONObject` validieren.

**`Missing fields`**
- Ursache: Der Request enthält weder `degrees`+`speed` noch `command`.
- Lösung: Request-Struktur prüfen.

### 4.2 Verbindungsfehler (Android-seitig)

| Fehler                        | Ursache                                              | Lösung                                    |
|-------------------------------|------------------------------------------------------|-------------------------------------------|
| `ConnectException`            | ESP8266 nicht erreichbar (falsche IP, kein WLAN)    | IP prüfen, WLAN-Verbindung sicherstellen  |
| `SocketTimeoutException`      | Netzwerklatenz zu hoch, Paket verloren              | Timeout erhöhen, Reconnect-Logik          |
| `onFailure` mit Code 1001     | WebSocket-Verbindung vom Server getrennt            | Reconnect mit Backoff                     |
| `onClosed` Code 1000          | Normale Trennung (z.B. zweiter Client verbindet)    | Reconnect oder Nutzer informieren         |

### 4.3 Watchdog-verursachter Disconnect

Wenn der ESP8266 innerhalb von 5 Sekunden keine WebSocket-Aktivität registriert
(kein Befehl, kein Ping), trennt er die Verbindung und stoppt den Motor.

Sende dazu alle 3 Sekunden einen WebSocket-Ping (OkHttp macht das automatisch,
wenn du `pingInterval` konfigurierst) oder einen Status-Query als Keepalive.

---

## 5. Android Kotlin Code-Beispiele mit OkHttp

### 5.1 Gradle-Abhängigkeit

In `build.gradle` (App-Modul):
```gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
}
```

### 5.2 AndroidManifest.xml Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Für unverschlüsselte WebSocket-Verbindungen (`ws://`) auf Android 9+:
```xml
<application
    android:usesCleartextTraffic="true"
    ...>
```

Alternativ eine Network Security Config erstellen (empfohlen für Produktion):
```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">192.168.1.42</domain>
    </domain-config>
</network-security-config>
```

Und in der `AndroidManifest.xml`:
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

### 5.3 Callback-Interface

```kotlin
interface StepperCallback {
    fun onConnected()
    fun onDisconnected(reason: String)
    fun onMovementComplete(degreesMoved: Float)
    fun onStatusReceived(status: String, positionDegrees: Float)
    fun onWarning(message: String)
    fun onError(message: String)
    fun onConnectionError(error: Throwable)
}
```

### 5.4 Vollständige StepperMotorClient-Klasse (Übersicht)

Die vollständige, produktionsbereite Implementierung befindet sich in
`docs/ANDROID_SAMPLE_CODE.kt`. Hier eine Übersicht der Schnittstelle:

```kotlin
class StepperMotorClient(private val callback: StepperCallback) {

    // Verbindung zum ESP8266 herstellen
    fun connect(ipAddress: String, port: Int = 81)

    // Motor bewegen: degrees > 0 = CW, degrees < 0 = CCW
    // speedDps: 1.0 bis 79.0 Grad/Sekunde
    fun moveMotor(degrees: Float, speedDps: Float)

    // Aktuellen Status abfragen
    fun getStatus()

    // Verbindung sauber trennen
    fun disconnect()

    // Ist gerade eine WebSocket-Verbindung aktiv?
    val isConnected: Boolean
}
```

### 5.5 Verwendung im ViewModel / Activity

```kotlin
// Initialisierung
val client = StepperMotorClient(object : StepperCallback {
    override fun onConnected() {
        runOnUiThread { statusText.text = "Verbunden" }
    }
    override fun onMovementComplete(degreesMoved: Float) {
        runOnUiThread { statusText.text = "Fertig: ${degreesMoved}°" }
    }
    override fun onStatusReceived(status: String, positionDegrees: Float) {
        runOnUiThread { positionText.text = "Position: ${positionDegrees}°" }
    }
    override fun onError(message: String) {
        runOnUiThread { showToast("Fehler: $message") }
    }
    override fun onWarning(message: String) {
        runOnUiThread { showToast("Warnung: $message") }
    }
    override fun onDisconnected(reason: String) {
        runOnUiThread { statusText.text = "Getrennt: $reason" }
    }
    override fun onConnectionError(error: Throwable) {
        runOnUiThread { statusText.text = "Verbindungsfehler" }
    }
})

// Verbinden
client.connect("192.168.1.42")

// Motor 90° im Uhrzeigersinn mit 45°/s
client.moveMotor(90.0f, 45.0f)

// Status abfragen
client.getStatus()

// Verbindung trennen (z.B. in onDestroy)
client.disconnect()
```

---

## 6. Grenzen und Einschränkungen

| Einschränkung                   | Wert / Verhalten                                              |
|---------------------------------|---------------------------------------------------------------|
| Gleichzeitige Clients           | **Maximal 1** – ein zweiter Verbindungsversuch schlägt fehl oder trennt den ersten Client |
| Geschwindigkeits-Untergrenze    | 1.0 °/s (physikalisches Minimum für stabilen Lauf)           |
| Geschwindigkeits-Obergrenze     | 79.0 °/s (physikalisches Maximum des 28BYJ-48 im Half-Step)  |
| Speed-Clipping                  | Werte außerhalb [1.0, 79.0] werden automatisch geclippt      |
| Winkelauflösung                 | ~0.088° (1 Half-Step), effektiv nutzbar ab 1°                |
| Degrees-Hard-Limit              | Keines – beliebige Umdrehungszahl möglich                     |
| Position nach Neustart          | Wird auf 0.0 zurückgesetzt (keine persistente Speicherung)   |
| TLS / WSS                       | Nicht unterstützt in v1.0                                    |
| Rückmeldung während Bewegung    | Keine Zwischenstatus-Updates – nur finale `ok`-Response      |
| Heap-Warnung                    | Bei < 8 KB freiem Heap gibt der ESP8266 eine Log-Warnung aus  |

---

## 7. Verbindungsverlust-Verhalten (Watchdog)

Der ESP8266 implementiert einen Software-Watchdog, der den Motor sauber stoppt,
wenn die WebSocket-Verbindung abbricht oder inaktiv wird.

### Watchdog-Auslöser

1. **Expliziter WebSocket-Close**: Client sendet Close-Frame → sofortiger Motor-Stop.
2. **Netzwerkverlust**: TCP-Verbindung bricht ab → Motor-Stop innerhalb von 5 Sekunden.
3. **Inaktivitäts-Timeout**: Kein Ping/Pong oder Daten-Frame innerhalb von 5 Sekunden
   → Motor-Stop + Verbindungstrennung.

### Maximale Nachlaufzeit

Nach dem Watchdog-Auslöser prüft der `stepper_task` das Stop-Flag nach jedem
einzelnen Half-Step. Bei maximaler Geschwindigkeit (79°/s) beträgt die maximale
Nachlaufzeit **~1,11 ms** (ein Half-Step-Delay). Dies entspricht einer
Winkelnachlauf von maximal **0,088°** – für alle praktischen Anwendungen
vernachlässigbar.

### Keepalive-Strategie

Um den Watchdog bei langen Bewegungen (> 5 Sekunden) zu verhindern, konfiguriere
OkHttp mit einem automatischen Ping-Intervall:

```kotlin
val client = OkHttpClient.Builder()
    .pingInterval(3, TimeUnit.SECONDS)  // alle 3s Ping senden
    .build()
```

Der ESP8266 antwortet auf WebSocket-Pings mit Pong-Frames, was den Watchdog-Timer
zurücksetzt. **3 Sekunden Intervall bei 5 Sekunden Watchdog-Timeout** lässt
ausreichend Puffer für Netzwerk-Jitter.

---

## 8. Tipps für robuste Integration

### 8.1 UI-Thread-Safety

OkHttp-WebSocket-Callbacks laufen auf einem Background-Thread. UI-Updates müssen
auf den Main Thread zurück. Optionen:

```kotlin
// Option A: Handler (klassisch)
Handler(Looper.getMainLooper()).post { /* UI-Update */ }

// Option B: Activity.runOnUiThread
runOnUiThread { textView.text = "..." }

// Option C: LiveData im ViewModel (empfohlen für größere Apps)
_statusLiveData.postValue("neuer Status")
```

Die `StepperMotorClient`-Klasse in `ANDROID_SAMPLE_CODE.kt` übernimmt das
Main-Thread-Dispatch intern über einen `Handler`.

### 8.2 Reconnect-Strategie

Verwende exponentiellen Backoff:

| Versuch | Wartezeit |
|---------|-----------|
| 1       | 1 Sekunde |
| 2       | 2 Sekunden |
| 3       | 4 Sekunden |
| 4+      | 8 Sekunden (Maximum) |

Die vollständige Reconnect-Logik ist in `ANDROID_SAMPLE_CODE.kt` implementiert.

### 8.3 Sequenzielle Befehle (Motor-Queue auf Client-Seite)

Da der ESP8266 nur 1 Befehl gleichzeitig verarbeitet, solltest du auf
Client-Seite keine neuen `moveMotor`-Befehle senden, bevor die vorherige
`ok`-Response eingegangen ist. Implementiere dazu ein einfaches
`isMoving`-Flag:

```kotlin
private var isMoving = false

fun moveMotor(degrees: Float, speedDps: Float) {
    if (isMoving) {
        callback.onError("Motor ist noch in Bewegung")
        return
    }
    isMoving = true
    // ... JSON senden ...
}

// In onMessage, wenn "status" == "ok":
isMoving = false
callback.onMovementComplete(degreesMoved)
```

### 8.4 Netzwerk-Prüfung vor Verbindung

```kotlin
fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
           caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}
```

Verbinde dich nur, wenn WLAN aktiv ist – sonst ist der ESP8266 nicht erreichbar.

### 8.5 Lifecycle-Management

Trenne die WebSocket-Verbindung sauber in `onDestroy()` oder `onStop()`, um den
Watchdog auf dem ESP8266 auszulösen und den Motor zu stoppen, wenn die App
geschlossen wird:

```kotlin
override fun onDestroy() {
    super.onDestroy()
    client.disconnect()
}
```

### 8.6 Timeout beim Warten auf Response

Für `moveMotor`-Befehle mit großen Winkeln kann die Antwort lange dauern.
Implementiere einen clientseitigen Timeout (z.B. über `Handler.postDelayed`),
um auf ausbleibende Antworten zu reagieren, ohne die App einzufrieren.

Beispiel: 360° bei 10°/s dauert 36 Sekunden. 3600° bei 1°/s dauert 60 Minuten.
Plane deine UI entsprechend (Progressindikator, Abbrechen-Button).

---

## Anhang: WebSocket-URL-Schema

```
ws://<ip>:<port><uri>
     ─────  ────  ───
       │      │    └── Immer: /ws
       │      └─────── Immer: 81
       └────────────── IP-Adresse des ESP8266 im lokalen WLAN
```

Beispiel: `ws://192.168.1.42:81/ws`
