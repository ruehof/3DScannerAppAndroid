package com.example.scanner3d.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * Der Ausschnitt hat eine Mindestgröße von 5 % der Vorschaufläche
 * und kann nicht über den Rand hinausgeschoben werden.
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
    // Welcher Teil wird gerade gezogen
    var dragTarget by remember { mutableStateOf(DragTarget.NONE) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(state) {
                val handleRadiusPx = 36.dp.toPx()   // Trefferbereich für Eck-Handles
                detectDragGestures(
                    onDragStart = { offset ->
                        val l = state.left   * size.width
                        val t = state.top    * size.height
                        val r = state.right  * size.width
                        val b = state.bottom * size.height
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
                        val dx = drag.x / size.width
                        val dy = drag.y / size.height
                        val MIN = 0.05f          // Mindestgröße 5 % der Vorschaubreite/-höhe
                        onStateChange(
                            when (dragTarget) {
                                DragTarget.MOVE -> state.copy(
                                    left   = (state.left   + dx).coerceIn(0f, state.right  - MIN),
                                    top    = (state.top    + dy).coerceIn(0f, state.bottom - MIN),
                                    right  = (state.right  + dx).coerceIn(state.left + MIN, 1f),
                                    bottom = (state.bottom + dy).coerceIn(state.top  + MIN, 1f)
                                )
                                DragTarget.TOP_LEFT -> state.copy(
                                    left = (state.left + dx).coerceIn(0f, state.right  - MIN),
                                    top  = (state.top  + dy).coerceIn(0f, state.bottom - MIN)
                                )
                                DragTarget.TOP_RIGHT -> state.copy(
                                    right = (state.right + dx).coerceIn(state.left + MIN, 1f),
                                    top   = (state.top   + dy).coerceIn(0f, state.bottom - MIN)
                                )
                                DragTarget.BOTTOM_LEFT -> state.copy(
                                    left   = (state.left   + dx).coerceIn(0f, state.right  - MIN),
                                    bottom = (state.bottom + dy).coerceIn(state.top  + MIN, 1f)
                                )
                                DragTarget.BOTTOM_RIGHT -> state.copy(
                                    right  = (state.right  + dx).coerceIn(state.left + MIN, 1f),
                                    bottom = (state.bottom + dy).coerceIn(state.top  + MIN, 1f)
                                )
                                DragTarget.NONE -> state
                            }
                        )
                    }
                )
            }
    ) {
        val l = state.left   * size.width
        val t = state.top    * size.height
        val r = state.right  * size.width
        val b = state.bottom * size.height

        // ── Abdunkelung außerhalb des Ausschnitts ────────────────────────────
        val shadow = Color.Black.copy(alpha = 0.50f)
        // oben
        drawRect(shadow, topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(size.width, t))
        // unten
        drawRect(shadow, topLeft = Offset(0f, b),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - b))
        // links (zwischen Rahmen-oben und -unten)
        drawRect(shadow, topLeft = Offset(0f, t),
            size = androidx.compose.ui.geometry.Size(l, b - t))
        // rechts
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
            drawCircle(Color.White,          radius = handleR, center = c)
            drawCircle(Color(0xFF222222), radius = innerR, center = c)
        }
    }
}

// Hilfsfunktion: euklidischer Abstand zwischen zwei Punkten
private fun dist(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}
