package com.example.scanner3d.presentation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanner3d.presentation.viewmodel.SettingsViewModel

/**
 * Einstellungen-Screen.
 * Konfiguriert: IP-Adresse, Motorgeschwindigkeit, Pause, Scan-Parameter,
 * Auslösegeräusch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) {
            snackbarHostState.showSnackbar("Einstellungen gespeichert")
            viewModel.clearSavedFlag()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zur\u00fcck"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // === Verbindung ===
            SettingsSectionCard(title = "Verbindung") {
                OutlinedTextField(
                    value = uiState.settings.espIpAddress,
                    onValueChange = viewModel::updateIpAddress,
                    label = { Text("ESP8266 IP-Adresse") },
                    placeholder = { Text("z.B. 192.168.1.100") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "IP-Adresse des ESP8266 im lokalen WLAN (Port 81 wird automatisch verwendet)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === Scan-Parameter ===
            SettingsSectionCard(title = "Scan-Parameter") {
                // Anzahl Fotos
                val stepDegrees = if (uiState.settings.numPhotos > 0)
                    uiState.settings.totalDegrees / uiState.settings.numPhotos else 0f
                SettingsSliderRow(
                    label = "Anzahl Fotos",
                    value = uiState.settings.numPhotos.toFloat(),
                    valueLabel = "${uiState.settings.numPhotos} Fotos  \u2022  ${String.format("%.1f", stepDegrees)}\u00b0/Schritt",
                    valueRange = 6f..36f,
                    steps = 29,
                    onValueChange = { viewModel.updateNumPhotos(it.toInt()) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Gesamtwinkel
                SettingsSliderRow(
                    label = "Gesamtwinkel",
                    value = uiState.settings.totalDegrees,
                    valueLabel = "${uiState.settings.totalDegrees.toInt()}\u00b0",
                    valueRange = 90f..360f,
                    steps = 269,
                    onValueChange = viewModel::updateTotalDegrees
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "F\u00fcr einen vollst\u00e4ndigen Rundum-Scan: 360\u00b0",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === Motor ===
            SettingsSectionCard(title = "Motor") {
                SettingsSliderRow(
                    label = "Drehgeschwindigkeit",
                    value = uiState.settings.motorSpeedDps,
                    valueLabel = "${uiState.settings.motorSpeedDps.toInt()} \u00b0/Sek.",
                    valueRange = 1f..79f,
                    steps = 77,
                    onValueChange = viewModel::updateMotorSpeed
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Empfehlung: 30\u201350 \u00b0/Sek. f\u00fcr stabile Scans. Maximum: 79 \u00b0/Sek.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsSliderRow(
                    label = "Pause nach Bewegung",
                    value = uiState.settings.pauseAfterMoveMs.toFloat(),
                    valueLabel = "${uiState.settings.pauseAfterMoveMs} ms",
                    valueRange = 0f..5000f,
                    steps = 99,
                    onValueChange = { viewModel.updatePauseAfterMove(it.toLong()) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Wartezeit nach dem Stopp, damit das Objekt aufh\u00f6rt zu schwingen.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === Kamera ===
            SettingsSectionCard(title = "Kamera") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ausl\u00f6seger\u00e4usch",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Klick-Ton bei jeder Aufnahme",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.settings.shutterSoundEnabled,
                        onCheckedChange = viewModel::updateShutterSoundEnabled
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::saveSettings,
                enabled = !uiState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = if (uiState.isSaving) "Speichert\u2026" else "Einstellungen speichern",
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tipps für Photogrammetrie-Nutzer
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tipps f\u00fcr Photogrammetrie",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\u2022 Fotos werden unter DCIM/3DScanner gespeichert\n" +
                               "\u2022 Empfohlen: 24\u201336 Fotos bei 360\u00b0\n" +
                               "\u2022 Pause von 1000\u20132000 ms f\u00fcr schwere Objekte\n" +
                               "\u2022 Gleichm\u00e4\u00dfige Beleuchtung verbessert das 3D-Modell\n" +
                               "\u2022 Kompatibel mit Meshroom, RealityCapture und COLMAP",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = valueLabel,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = Modifier.fillMaxWidth()
    )
}
