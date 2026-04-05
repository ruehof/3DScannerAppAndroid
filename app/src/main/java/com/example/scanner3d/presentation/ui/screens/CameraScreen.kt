package com.example.scanner3d.presentation.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanner3d.presentation.ui.components.CameraPreviewView
import com.example.scanner3d.presentation.ui.components.ScanControlPanel
import com.example.scanner3d.presentation.ui.components.createPreviewView
import com.example.scanner3d.presentation.viewmodel.CameraViewModel
import com.example.scanner3d.presentation.viewmodel.ScanState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "CameraScreen"

/**
 * Haupt-Kamera-Screen.
 *
 * Layout: CameraX-Preview füllt den gesamten Bildschirm (wie bei der Standard-Kamera-App).
 * Unten: halbtransparentes Kontroll-Panel mit Scan-Parametern und Start-Button.
 * Oben rechts: Einstellungen-Icon.
 * Unten rechts: Einzelbild-Capture-Button.
 */
@Composable
fun CameraScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // CameraX-Objekte
    val previewView = remember { createPreviewView(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Kamera-Berechtigung – native Implementierung ohne Accompanist
    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedPermanently by rememberSaveable { mutableStateOf(false) }
    var showRationale by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            // Wenn abgelehnt, Rationale anzeigen
            showRationale = true
        }
    }

    // Beim ersten Start Berechtigung prüfen/anfordern
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Fehlermeldungen als Snackbar anzeigen
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    // Fertigmeldung nach Scan
    LaunchedEffect(uiState.scanState) {
        if (uiState.scanState is ScanState.Complete) {
            val count = (uiState.scanState as ScanState.Complete).photoCount
            snackbarHostState.showSnackbar("$count Fotos gespeichert in DCIM/3DScanner")
        }
    }

    // CameraX initialisieren wenn Berechtigung erteilt
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageCaptureUseCase = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                imageCapture = imageCaptureUseCase

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCaptureUseCase
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "CameraX Bindung fehlgeschlagen", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    // Berechtigung abgelehnt – Rationale-Dialog anzeigen
    if (showRationale && !hasCameraPermission) {
        CameraPermissionDialog(
            onRequestPermission = {
                showRationale = false
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = {
                showRationale = false
                permissionDeniedPermanently = true
            }
        )
        return
    }

    // Berechtigung dauerhaft abgelehnt
    if (permissionDeniedPermanently && !hasCameraPermission) {
        CameraPermissionDeniedScreen()
        return
    }

    // Warten auf Berechtigung
    if (!hasCameraPermission) {
        return
    }

    /** Hilfsfunktion: Einzelfoto aufnehmen und URI zurückgeben */
    suspend fun capturePhoto(index: Int): Result<Uri> {
        val capture = imageCapture ?: return Result.failure(Exception("Kamera nicht bereit"))
        val deferred = CompletableDeferred<Result<Uri>>()

        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "scan_${dateFormat.format(Date())}_${"%03d".format(index)}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/3DScanner")
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    deferred.complete(Result.success(output.savedUri ?: Uri.EMPTY))
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Foto-Fehler: ${exc.message}", exc)
                    deferred.complete(Result.failure(exc))
                }
            }
        )
        return deferred.await()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Kamera-Vollbild-Vorschau
            CameraPreviewView(
                previewView = previewView,
                modifier = Modifier.fillMaxSize()
            )

            // Einstellungen-Button oben rechts
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Einstellungen",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Einzelbild-Button rechts (nur wenn nicht gescannt wird)
            if (!uiState.isScanning) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            capturePhoto(0)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 260.dp, end = 16.dp),
                    shape = CircleShape,
                    containerColor = Color.White.copy(alpha = 0.2f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Einzelfoto",
                        tint = Color.White
                    )
                }
            }

            // Kontroll-Panel unten
            ScanControlPanel(
                uiState = uiState,
                onNumPhotosChange = viewModel::setNumPhotos,
                onTotalDegreesChange = viewModel::setTotalDegrees,
                onStartScan = {
                    viewModel.startScan { index -> capturePhoto(index) }
                },
                onCancelScan = viewModel::cancelScan,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun CameraPermissionDialog(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kamera-Berechtigung erforderlich") },
        text = {
            Text("Die App benoetigt Zugriff auf die Kamera, um Fotos fuer den 3D-Scan aufzunehmen.")
        },
        confirmButton = {
            Button(onClick = onRequestPermission) {
                Text("Berechtigung erteilen")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun CameraPermissionDeniedScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Kamera-Berechtigung benoetigt",
                color = Color.White
            )
            Text(
                text = "Bitte erteile die Berechtigung in den App-Einstellungen.",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp)
            )
        }
    }
}
