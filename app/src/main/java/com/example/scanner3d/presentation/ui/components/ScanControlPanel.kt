package com.example.scanner3d.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Zeigt Verbindungsstatus, Scan-Parameter und Scan-Start/Stop-Button.
 */
@Composable
fun ScanControlPanel(
    uiState: CameraUiState,
    onNumPhotosChange: (Int) -> Unit,
    onTotalDegreesChange: (Float) -> Unit,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
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

        Spacer(modifier = Modifier.height(12.dp))

        // Nur im Idle-Zustand: Parameter anzeigen
        if (!uiState.isScanning && uiState.scanState !is ScanState.Scanning) {
            ScanParameterControls(
                numPhotos = uiState.numPhotos,
                totalDegrees = uiState.totalDegrees,
                stepDegrees = uiState.stepDegrees,
                onNumPhotosChange = onNumPhotosChange,
                onTotalDegreesChange = onTotalDegreesChange
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Fortschrittsanzeige während Scan
        if (uiState.scanState is ScanState.Scanning) {
            ScanProgressIndicator(scanState = uiState.scanState)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Scan abgeschlossen
        if (uiState.scanState is ScanState.Complete) {
            Text(
                text = "Fertig: ${uiState.scanState.photoCount} Fotos gespeichert in DCIM/3DScanner",
                color = ScanActive,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Scan-Fehler
        if (uiState.scanState is ScanState.Error) {
            Text(
                text = "Fehler: ${uiState.scanState.message}",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Start/Stop-Button
        ScanActionButton(
            uiState = uiState,
            onStartScan = onStartScan,
            onCancelScan = onCancelScan
        )
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

@Composable
private fun ScanParameterControls(
    numPhotos: Int,
    totalDegrees: Float,
    stepDegrees: Float,
    onNumPhotosChange: (Int) -> Unit,
    onTotalDegreesChange: (Float) -> Unit
) {
    // Anzahl Fotos (4 – 72)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Anzahl Fotos", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "$numPhotos Fotos  \u2022  ${String.format("%.1f", stepDegrees)}\u00b0 / Schritt",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Slider(
            value = numPhotos.toFloat(),
            onValueChange = { onNumPhotosChange(it.toInt()) },
            valueRange = 4f..72f,
            steps = 67,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Gesamtwinkel (90 – 360°)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Gesamtwinkel", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "${totalDegrees.toInt()}\u00b0",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Slider(
            value = totalDegrees,
            onValueChange = { onTotalDegreesChange(it) },
            valueRange = 90f..360f,
            steps = 269,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
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
