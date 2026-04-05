# Android Quick Start: ESP8266 Schrittmotor WebSocket-API

## 1. Gradle-Abhängigkeit

In `build.gradle` (App-Modul, nicht Projekt-Root):

```gradle
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
}
```

---

## 2. AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.INTERNET" />

<application
    android:usesCleartextTraffic="true"
    ...>
```

`usesCleartextTraffic` ist notwendig, weil die Verbindung unverschlüsselt (`ws://`)
ist. Ohne diesen Eintrag wirft Android 9+ eine `SSLHandshakeException` oder
blockiert die Verbindung stillschweigend.

---

## 3. Minimal-Beispiel: Motor 90 Grad drehen

```kotlin
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

val client = OkHttpClient.Builder()
    .pingInterval(3, TimeUnit.SECONDS)
    .build()

val request = Request.Builder()
    .url("ws://192.168.1.42:81/ws")
    .build()

client.newWebSocket(request, object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        val cmd = JSONObject()
        cmd.put("degrees", 90.0)
        cmd.put("speed", 45.0)
        webSocket.send(cmd.toString())
    }
    override fun onMessage(webSocket: WebSocket, text: String) {
        println("Antwort: $text")   // {"status":"ok","degrees_moved":90.0}
        webSocket.close(1000, "Fertig")
    }
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        println("Fehler: ${t.message}")
    }
})
```

Ersetze `192.168.1.42` durch die tatsächliche IP-Adresse deines ESP8266.

---

## 4. Häufige Fehler und Lösungen

### ConnectException / Connection refused

**Symptom:** `java.net.ConnectException: Failed to connect to /192.168.1.42:81`

**Ursachen und Lösungen:**
- Falsche IP-Adresse: IP im Router-Admin-Interface unter DHCP-Clients nachschlagen.
- ESP8266 nicht im WLAN: Seriellen Monitor öffnen (115200 Baud), auf
  `I (...) wifi_manager: IP-Adresse: ...` warten.
- Falscher Port: Port muss **81** sein, nicht 80 oder 8080.
- ESP8266 noch nicht fertig gestartet: ~3–5 Sekunden nach dem Einschalten warten.

---

### CLEARTEXT-Fehler (Android 9+)

**Symptom:** `CLEARTEXT communication to 192.168.1.x not permitted by network security policy`

**Lösung:** In `AndroidManifest.xml` im `<application>`-Tag hinzufügen:
```xml
android:usesCleartextTraffic="true"
```

---

### Motor antwortet nicht auf Befehle

**Symptom:** `onOpen` wird aufgerufen, aber nach `send()` kommt keine Antwort.

**Ursachen:**
- ESP8266 noch beim WLAN-Verbindungsaufbau: `onOpen` erst aufrufen, wenn wirklich
  bereit. Normalerweise ist der WebSocket-Server erst nach vollständigem WLAN-Start
  erreichbar, also sollte dies nicht vorkommen.
- Motor läuft noch: Warte auf `{"status":"ok",...}` bevor du einen neuen Befehl sendest.
  Bei `{"status":"error","message":"Motor busy"}` kurz warten und nochmals senden.

---

### Verbindung wird nach ~5 Sekunden getrennt

**Symptom:** `onClosed` oder `onFailure` nach wenigen Sekunden Inaktivität.

**Ursache:** ESP8266-Watchdog – kein Ping innerhalb von 5 Sekunden.

**Lösung:** OkHttp mit `pingInterval` konfigurieren:
```kotlin
OkHttpClient.Builder()
    .pingInterval(3, TimeUnit.SECONDS)
    .build()
```

---

### `JSONException` beim Parsen der Response

**Symptom:** App crasht beim Verarbeiten der ESP8266-Antwort.

**Lösung:** `optString`/`optDouble` statt `getString`/`getDouble` verwenden,
oder mit `try/catch` wrappen:
```kotlin
try {
    val obj = JSONObject(text)
    val status = obj.optString("status", "unknown")
    val degreesMoved = obj.optDouble("degrees_moved", 0.0).toFloat()
} catch (e: Exception) {
    // Unbekanntes Format ignorieren oder loggen
}
```

---

### Zweiter Client kann sich nicht verbinden

**Symptom:** Verbindungsaufbau schlägt fehl, obwohl der ESP8266 läuft.

**Ursache:** Der ESP8266 erlaubt in v1.0 nur **1 gleichzeitigen Client**. Eine
bereits aktive Verbindung muss erst getrennt werden.

---

## 5. Referenz: Alle Request/Response-Formate

```
// Motor bewegen
→  {"degrees": 90.0, "speed": 45.0}
←  {"status": "ok", "degrees_moved": 90.0}
←  {"status": "error", "message": "Motor busy"}

// Status abfragen
→  {"command": "status"}
←  {"status": "idle", "position": 270.0}
←  {"status": "moving", "position": 270.0}
```

Speed-Bereich: **1.0 bis 79.0 °/s** – Werte außerhalb werden automatisch geclippt.
Degrees: positiv = CW (Uhrzeigersinn), negativ = CCW (Gegenuhrzeigersinn).

---

Vollständige Dokumentation: `docs/ANDROID_API_GUIDE.md`
Produktionsbereiter Kotlin-Code: `docs/ANDROID_SAMPLE_CODE.kt`
