package com.example.scanner3d.presentation.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * Unteres Kontroll-Panel für den Kamera-Screen.
 *
 * Zeigt:
 * - Verbindungsstatus mit Connect/Disconnect-Button
 * - Scan-Parameter (aus Einstellungen, nur lesend)
 * - Fortschrittsanzeige während Scan
 * - Start/Stop-Button
 * - Manuelle Rotationsbuttons (Kurzklick = 3,6°, Langklick = 36°)
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Verbindungszeile
        ConnectionStatusRow(
            connectionState = uiState.connectionState,
            onConnect = onConnect,
            onDisconnect = onDisconnect
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Scan-Parameter-Anzeige (nur lesend; Einstellung via Einstellungen-Screen)
        if (!uiState.isScanning) {
            ScanParameterInfo(
                numPhotos = uiState.numPhotos,
                totalDegrees = uiState.totalDegrees,
                stepDegrees = uiState.stepDegrees
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Fortschrittsanzeige während Scan
        if (uiState.scanState is ScanState.Scanning) {
            ScanProgressIndicator(scanState = uiState.scanState)
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Scan abgeschlossen
        if (uiState.scanState is ScanState.Complete) {
            Text(
                text = "\u2713 ${uiState.scanState.photoCount} Fotos gespeichert in DCIM/3DScanner",
                color = ScanActive,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Scan-Fehler
        if (uiState.scanState is ScanState.Error) {
            Text(
                text = "\u26a0 ${uiState.scanState.message}",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // 3D-Scan starten / abbrechen
        ScanActionButton(
            uiState = uiState,
            onStartScan = onStartScan,
            onCancelScan = onCancelScan
        )

        // Manuelle Rotationsbuttons (nur wenn verbunden und kein Scan läuft)
        if (!uiState.isScanning) {
            Spacer(modifier = Modifier.height(10.dp))
            ManualRotationButtons(
                enabled = uiState.connectionState is ConnectionState.Connected,
                onRotateLeft = onRotateLeft,
                onRotateRight = onRotateRight
            )
        }
    }
}

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
                    is ConnectionState.Connected -> "Verbunden"
                    is ConnectionState.Connecting -> "Verbinde\u2026"
                    is ConnectionState.Disconnected -> "Getrennt"
                    is ConnectionState.Error -> "Fehler: ${connectionState.message}"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        when (connectionState) {
            is ConnectionState.Connected -> {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.height(32.dp)
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
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Verbinden", fontSize = 11.sp)
                }
            }
        }
    }
}

/** Zeigt die aktuell eingestellten Scan-Parameter (nur lesend). */
@Composable
private fun ScanParameterInfo(
    numPhotos: Int,
    totalDegrees: Float,
    stepDegrees: Float
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ScanParamChip(label = "Fotos", value = "$numPhotos")
            ScanParamChip(label = "Winkel", value = "${totalDegrees.toInt()}\u00b0")
            ScanParamChip(label = "Schritt", value = "${String.format("%.1f", stepDegrees)}\u00b0")
        }
    }
}

@Composable
private fun ScanParamChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScanProgressIndicator(scanState: ScanState.Scanning) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Foto ${scanState.current} von ${scanState.total}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${(scanState.current * 100 / scanState.total)}%",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
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

@Composable
private fun ScanActionButton(
    uiState: CameraUiState,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit
) {
    val isScanning = uiState.isScanning
    val isConnected = uiState.connectionState is ConnectionState.Connected

    Button(
        onClick = if (isScanning) onCancelScan else onStartScan,
        enabled = isConnected || isScanning,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isScanning) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
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

/**
 * Zwei Buttons zum manuellen Drehen des Drehtellers.
 *
 * Kurzklick  = 3,6° (feinjustierung)
 * Langklick  = 36°  (grobe Justierung, wird mit Haptik-Feedback bestätigt)
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
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Links-Button (CCW)
        RotationButton(
            icon = { Icon(Icons.Default.RotateLeft, contentDescription = "Links drehen",
                modifier = Modifier.size(22.dp)) },
            label = "Links",
            sublabel = "kurz 3,6\u00b0 \u2022 lang 36\u00b0",
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onRotateLeft(3.6f) },
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRotateLeft(36f)
            }
        )

        // Rechts-Button (CW)
        RotationButton(
            icon = { Icon(Icons.Default.RotateRight, contentDescription = "Rechts drehen",
                modifier = Modifier.size(22.dp)) },
            label = "Rechts",
            sublabel = "kurz 3,6\u00b0 \u2022 lang 36\u00b0",
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onRotateRight(3.6f) },
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRotateRight(36f)
            }
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
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val contentColor = if (enabled)
        MaterialTheme.colorScheme.onSecondaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Box(
        modifier = modifier
            .height(56.dp)
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
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                // Icon mit contentColor tint wird über LocalContentColor gesetzt
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides contentColor
                ) {
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
