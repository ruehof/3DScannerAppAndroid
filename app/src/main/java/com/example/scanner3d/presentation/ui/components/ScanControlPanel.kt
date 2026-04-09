package com.example.scanner3d.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanner3d.domain.model.ConnectionState
import com.example.scanner3d.presentation.ui.theme.Connected
import com.example.scanner3d.presentation.ui.theme.Disconnected
import com.example.scanner3d.presentation.ui.theme.ScanActive
import com.example.scanner3d.presentation.viewmodel.CameraUiState
import com.example.scanner3d.presentation.viewmodel.ScanState

// Alpha-Werte zentral definiert – für konsistentes Glasmorphismus-Erscheinungsbild
private const val PANEL_ALPHA        = 0.78f   // Hintergrund des gesamten Panels
private const val BUTTON_ALPHA       = 0.82f   // Primär-Buttons (Scan, Verbinden)
private const val BUTTON_OUTLINE_ALPHA = 0.70f // OutlinedButton (Trennen)
private const val ROTATION_BTN_ALPHA = 0.75f   // Rotationsbuttons

/**
 * Unteres Kontroll-Panel für den Kamera-Screen.
 *
 * Features:
 * - Ein-/Ausblend-Toggle über den Pfeil-Handle oben
 * - Einzeilige Scan-Parameter-Anzeige (Label: Wert)
 * - Semitransparente Buttons (Glasmorphismus)
 * - Manuelle Rotationsbuttons (Kurzklick 3,6°, Langklick 36°)
 */
@Composable
fun ScanControlPanel(
    uiState: CameraUiState,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRotateLeft: (Float) -> Unit,
    onRotateRight: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Zustand für das Ein-/Ausblenden, lokal im Panel – kein Lift nötig
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = PANEL_ALPHA),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
    ) {
        // ── Toggle-Handle ──────────────────────────────────────────────────
        PanelToggleHandle(
            expanded = expanded,
            onToggle = { expanded = !expanded }
        )

        // ── Panel-Inhalt (animiert ein-/ausgeblendet) ──────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top),
            exit = shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {

                // Verbindungszeile
                ConnectionStatusRow(
                    connectionState = uiState.connectionState,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scan-Parameter einzeilig (nur wenn kein Scan läuft)
                if (!uiState.isScanning) {
                    ScanParameterRow(
                        numPhotos = uiState.numPhotos,
                        totalDegrees = uiState.totalDegrees,
                        stepDegrees = uiState.stepDegrees
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Fortschrittsanzeige während Scan
                if (uiState.scanState is ScanState.Scanning) {
                    ScanProgressIndicator(scanState = uiState.scanState)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Scan abgeschlossen
                if (uiState.scanState is ScanState.Complete) {
                    Text(
                        text = "\u2713 ${uiState.scanState.photoCount} Fotos gespeichert in DCIM/3DScanner",
                        color = ScanActive,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Scan-Fehler
                if (uiState.scanState is ScanState.Error) {
                    Text(
                        text = "\u26a0 ${uiState.scanState.message}",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // 3D-Scan starten / abbrechen
                ScanActionButton(
                    uiState = uiState,
                    onStartScan = onStartScan,
                    onCancelScan = onCancelScan
                )

                // Manuelle Rotationsbuttons (nur wenn kein Scan läuft)
                if (!uiState.isScanning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ManualRotationButtons(
                        enabled = uiState.connectionState is ConnectionState.Connected,
                        onRotateLeft = onRotateLeft,
                        onRotateRight = onRotateRight
                    )
                }
            }
        }
    }
}

// ── Toggle-Handle ──────────────────────────────────────────────────────────────

@Composable
private fun PanelToggleHandle(expanded: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Dekorativer Balken links
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(2.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(1.dp)
                    )
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown
                              else Icons.Default.KeyboardArrowUp,
                contentDescription = if (expanded) "Bedienfeld ausblenden" else "Bedienfeld einblenden",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            // Dekorativer Balken rechts
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(2.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

// ── Verbindungszeile ──────────────────────────────────────────────────────────

@Composable
private fun ConnectionStatusRow(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val isConnected = connectionState is ConnectionState.Connected
            Icon(
                imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = null,
                tint = if (isConnected) Connected else Disconnected,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when (connectionState) {
                    is ConnectionState.Connected    -> "Verbunden"
                    is ConnectionState.Connecting   -> "Verbinde\u2026"
                    is ConnectionState.Disconnected -> "Getrennt"
                    is ConnectionState.Error        -> "Fehler: ${connectionState.message}"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        when (connectionState) {
            is ConnectionState.Connected -> {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                            .copy(alpha = BUTTON_OUTLINE_ALPHA)
                    )
                ) {
                    Text("Trennen", fontSize = 11.sp)
                }
            }
            is ConnectionState.Connecting -> {
                Text("\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            else -> {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = BUTTON_ALPHA)
                    )
                ) {
                    Text("Verbinden", fontSize = 11.sp)
                }
            }
        }
    }
}

// ── Einzeilige Scan-Parameter-Anzeige ────────────────────────────────────────

/**
 * Zeigt Fotos, Winkel und Schritt in einer einzigen Zeile:
 * "Fotos: 12  •  Winkel: 360°  •  Schritt: 30,0°"
 */
@Composable
private fun ScanParameterRow(numPhotos: Int, totalDegrees: Float, stepDegrees: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ParamItem(label = "Fotos", value = "$numPhotos")
        Separator()
        ParamItem(label = "Winkel", value = "${totalDegrees.toInt()}\u00b0")
        Separator()
        ParamItem(label = "Schritt", value = "${String.format("%.1f", stepDegrees)}\u00b0")
    }
}

@Composable
private fun ParamItem(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "$label:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Separator() {
    Text(
        text = "\u2022",
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    )
}

// ── Fortschrittsanzeige ───────────────────────────────────────────────────────

@Composable
private fun ScanProgressIndicator(scanState: ScanState.Scanning) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Foto ${scanState.current} von ${scanState.total}",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(text = "${scanState.current * 100 / scanState.total}%",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { scanState.current.toFloat() / scanState.total },
            modifier = Modifier.fillMaxWidth(),
            color = ScanActive,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

// ── Scan-Start/Stop-Button ────────────────────────────────────────────────────

@Composable
private fun ScanActionButton(
    uiState: CameraUiState,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit
) {
    val isScanning  = uiState.isScanning
    val isConnected = uiState.connectionState is ConnectionState.Connected

    Button(
        onClick = if (isScanning) onCancelScan else onStartScan,
        enabled = isConnected || isScanning,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isScanning)
                MaterialTheme.colorScheme.error.copy(alpha = BUTTON_ALPHA)
            else
                MaterialTheme.colorScheme.primary.copy(alpha = BUTTON_ALPHA),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Icon(
            imageVector = if (isScanning) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isScanning) "Scan abbrechen" else "3D-Scan starten",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Manuelle Rotationsbuttons ─────────────────────────────────────────────────

/**
 * Kurzklick = 3,6° | Langklick = 36° (mit Haptik-Feedback)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManualRotationButtons(
    enabled: Boolean,
    onRotateLeft: (Float) -> Unit,
    onRotateRight: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RotationButton(
            icon = { Icon(Icons.Default.RotateLeft, contentDescription = "Links drehen",
                modifier = Modifier.size(22.dp)) },
            label = "Links",
            sublabel = "kurz 3,6\u00b0  \u2022  lang 36\u00b0",
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onRotateLeft(3.6f) },
            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onRotateLeft(36f) }
        )
        RotationButton(
            icon = { Icon(Icons.Default.RotateRight, contentDescription = "Rechts drehen",
                modifier = Modifier.size(22.dp)) },
            label = "Rechts",
            sublabel = "kurz 3,6\u00b0  \u2022  lang 36\u00b0",
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onRotateRight(3.6f) },
            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onRotateRight(36f) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RotationButton(
    icon: @Composable () -> Unit,
    label: String,
    sublabel: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val containerColor = if (enabled)
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = ROTATION_BTN_ALPHA)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    val contentColor = if (enabled)
        MaterialTheme.colorScheme.onSecondaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    icon()
                }
            }
            Column {
                Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = contentColor)
                Text(text = sublabel, fontSize = 9.sp, color = contentColor.copy(alpha = 0.7f))
            }
        }
    }
}
