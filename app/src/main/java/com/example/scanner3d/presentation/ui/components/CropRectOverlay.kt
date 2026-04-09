package com.example.scanner3d.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * Normalisierter Bildausschnitt. Alle Felder liegen im Bereich [0.0, 1.0]
 * relativ zur Vorschaufläche des Kamera-Feeds.
 */
data class CropRectState(
    val left: Float = 0.1f,
    val top: Float = 0.1f,
    val right: Float = 0.9f,
    val bottom: Float = 0.9f
)

/** Welcher Teil des Crop-Rahmens wird gerade gezogen? */
private enum class DragTarget {
    NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

/**
 * Interaktiver Bildausschnitt-Rahmen, der über den Kamera-Vorschau-Feed gelegt wird.
 *
 * Bedienung:
 * - Eck-Handles ziehen → Größe ändern
 * - Fläche innerhalb des Rahmens ziehen → Position ändern
 * - Außerhalb tippen/ziehen → keine Aktion
 *
 * Wichtig: Das [pointerInput]-Modifier verwendet [Unit] als Schlüssel und liest den
 * aktuellen [state] via [rememberUpdatedState]. Dadurch wird die laufende Drag-Geste
 * nicht bei jeder State-Änderung neu gestartet – das war der Grund, warum die
 * Größenänderung per Ziehen an den Ecken nicht funktioniert hat.
 *
 * @param state        Aktueller normalisierter Ausschnitt (0..1 je Koordinate)
 * @param onStateChange Callback, der bei jeder Änderung mit dem neuen Zustand gerufen wird
 * @param modifier     Weitere Modifier-Kette
 */
@Composable
fun CropRectOverlay(
    state: CropRectState,
    onStateChange: (CropRectState) -> Unit,
    modifier: Modifier = Modifier
) {
    // Letzten State und Callback im Gesture-Handler verfügbar machen, ohne
    // den pointerInput-Block neu zu starten (Unit-Schlüssel bleibt konstant).
    val currentState by rememberUpdatedState(state)
    val currentOnStateChange by rememberUpdatedState(onStateChange)

    // Welcher Teil wird gerade gezogen – nur während des Drags belegt
    var dragTarget by remember { mutableStateOf(DragTarget.NONE) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // Unit als Schlüssel → pointerInput wird NIEMALS neu gestartet.
            // Das ist entscheidend: bei Schlüssel = state würde jede Crop-Änderung
            // den Block neu starten und die laufende Geste abbrechen.
            .pointerInput(Unit) {
                val handleRadiusPx = 36.dp.toPx()   // Trefferbereich der Eck-Handles
                detectDragGestures(
                    onDragStart = { offset ->
                        // Aktuelle Crop-Koordinaten in Pixel
                        val s = currentState
                        val l = s.left   * size.width
                        val t = s.top    * size.height
                        val r = s.right  * size.width
                        val b = s.bottom * size.height
                        dragTarget = when {
                            dist(offset, Offset(l, t)) < handleRadiusPx -> DragTarget.TOP_LEFT
                            dist(offset, Offset(r, t)) < handleRadiusPx -> DragTarget.TOP_RIGHT
                            dist(offset, Offset(l, b)) < handleRadiusPx -> DragTarget.BOTTOM_LEFT
                            dist(offset, Offset(r, b)) < handleRadiusPx -> DragTarget.BOTTOM_RIGHT
                            offset.x in l..r && offset.y in t..b        -> DragTarget.MOVE
                            else                                          -> DragTarget.NONE
                        }
                    },
                    onDragEnd    = { dragTarget = DragTarget.NONE },
                    onDragCancel = { dragTarget = DragTarget.NONE },
                    onDrag = { _, drag ->
                        if (dragTarget == DragTarget.NONE) return@detectDragGestures
                        val s = currentState
                        val dx = drag.x / size.width
                        val dy = drag.y / size.height
                        val MIN = 0.05f          // Mindestgröße 5 % der Vorschau
                        currentOnStateChange(
                            when (dragTarget) {
                                DragTarget.MOVE -> s.copy(
                                    left   = (s.left   + dx).coerceIn(0f, s.right  - MIN),
                                    top    = (s.top    + dy).coerceIn(0f, s.bottom - MIN),
                                    right  = (s.right  + dx).coerceIn(s.left + MIN, 1f),
                                    bottom = (s.bottom + dy).coerceIn(s.top  + MIN, 1f)
                                )
                                DragTarget.TOP_LEFT -> s.copy(
                                    left = (s.left + dx).coerceIn(0f, s.right  - MIN),
                                    top  = (s.top  + dy).coerceIn(0f, s.bottom - MIN)
                                )
                                DragTarget.TOP_RIGHT -> s.copy(
                                    right = (s.right + dx).coerceIn(s.left + MIN, 1f),
                                    top   = (s.top   + dy).coerceIn(0f, s.bottom - MIN)
                                )
                                DragTarget.BOTTOM_LEFT -> s.copy(
                                    left   = (s.left   + dx).coerceIn(0f, s.right  - MIN),
                                    bottom = (s.bottom + dy).coerceIn(s.top  + MIN, 1f)
                                )
                                DragTarget.BOTTOM_RIGHT -> s.copy(
                                    right  = (s.right  + dx).coerceIn(s.left + MIN, 1f),
                                    bottom = (s.bottom + dy).coerceIn(s.top  + MIN, 1f)
                                )
                                DragTarget.NONE -> s
                            }
                        )
                    }
                )
            }
    ) {
        // ── Zeichnen mit dem aktuellen state-Parameter (wird bei Recomposition aktualisiert) ──

        val l = state.left   * size.width
        val t = state.top    * size.height
        val r = state.right  * size.width
        val b = state.bottom * size.height

        // ── Abdunkelung außerhalb des Ausschnitts ────────────────────────────
        val shadow = Color.Black.copy(alpha = 0.50f)
        drawRect(shadow, topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(size.width, t))
        drawRect(shadow, topLeft = Offset(0f, b),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - b))
        drawRect(shadow, topLeft = Offset(0f, t),
            size = androidx.compose.ui.geometry.Size(l, b - t))
        drawRect(shadow, topLeft = Offset(r, t),
            size = androidx.compose.ui.geometry.Size(size.width - r, b - t))

        // ── Gestrichelter Rahmen ─────────────────────────────────────────────
        drawRect(
            color = Color.White,
            topLeft = Offset(l, t),
            size = androidx.compose.ui.geometry.Size(r - l, b - t),
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
            )
        )

        // ── Drittel-Raster (Goldener-Schnitt-Hilfslinien) ───────────────────
        val grid = Color.White.copy(alpha = 0.22f)
        val sw = 1.dp.toPx()
        val w3 = (r - l) / 3f
        val h3 = (b - t) / 3f
        drawLine(grid, Offset(l + w3,     t), Offset(l + w3,     b), strokeWidth = sw)
        drawLine(grid, Offset(l + 2 * w3, t), Offset(l + 2 * w3, b), strokeWidth = sw)
        drawLine(grid, Offset(l, t + h3),     Offset(r, t + h3),     strokeWidth = sw)
        drawLine(grid, Offset(l, t + 2 * h3), Offset(r, t + 2 * h3), strokeWidth = sw)

        // ── Eck-Handles (ausgefüllte Kreise) ────────────────────────────────
        val handleR = 10.dp.toPx()
        val innerR  = handleR - 2.5.dp.toPx()
        listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { c ->
            drawCircle(Color.White,        radius = handleR, center = c)
            drawCircle(Color(0xFF222222),  radius = innerR,  center = c)
        }
    }
}

private fun dist(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}
