package com.example.scanner3d.presentation.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private const val TAG = "CameraScreen"

/**
 * Haupt-Kamera-Screen.
 *
 * Layout: CameraX-Preview füllt den gesamten Bildschirm (wie bei der Standard-Kamera-App).
 * Unten: halbtransparentes Kontroll-Panel mit Verbindung, Scan-Info, Start-Button und
 *        manuellen Rotationsbuttons.
 * Oben rechts: Einstellungen-Icon.
 * Unten rechts: Einzelbild-Capture-Button.
 * Kamera-Bereich: Antippen stellt scharf (Tap-to-Focus mit visuellem Indikator).
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
    // Camera-Referenz für Tap-to-Focus
    var camera by remember { mutableStateOf<Camera?>(null) }

    // Tap-to-Focus – Indikator-Position und Animationsstatus
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }
    val focusAlpha = remember { Animatable(0f) }

    // Auslösegeräusch (MediaActionSound – nutzt den systemeigenen Kamera-Sound)
    val mediaActionSound = remember { MediaActionSound() }
    DisposableEffect(Unit) {
        mediaActionSound.load(MediaActionSound.SHUTTER_CLICK)
        onDispose { mediaActionSound.release() }
    }

    // Kamera-Berechtigung
    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedPermanently by rememberSaveable { mutableStateOf(false) }
    var showRationale by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) showRationale = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
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

    // CameraX initialisieren, sobald Berechtigung erteilt
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
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
                camera = cameraProvider.bindToLifecycle(
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

    // Berechtigungsdialoge
    if (showRationale && !hasCameraPermission) {
        CameraPermissionDialog(
            onRequestPermission = { showRationale = false; permissionLauncher.launch(Manifest.permission.CAMERA) },
            onDismiss = { showRationale = false; permissionDeniedPermanently = true }
        )
        return
    }
    if (permissionDeniedPermanently && !hasCameraPermission) {
        CameraPermissionDeniedScreen(); return
    }
    if (!hasCameraPermission) return

    /** Einzelfoto aufnehmen, optional mit Auslösegeräusch */
    suspend fun capturePhoto(index: Int): Result<Uri> {
        val capture = imageCapture ?: return Result.failure(Exception("Kamera nicht bereit"))
        val deferred = CompletableDeferred<Result<Uri>>()

        // Auslösegeräusch abspielen (vor dem Capture, wie beim echten Kamera-Shutter)
        if (uiState.shutterSoundEnabled) {
            mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
        }

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

    val density = LocalDensity.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Kamera-Vollbild-Vorschau mit Tap-to-Focus
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(camera) {
                        detectTapGestures { tapOffset ->
                            val cam = camera ?: return@detectTapGestures
                            // Schärfe-Punkt via MeteringPoint-Factory setzen
                            val meteringPoint = previewView.meteringPointFactory
                                .createPoint(tapOffset.x, tapOffset.y)
                            val action = FocusMeteringAction.Builder(
                                meteringPoint,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                            )
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            cam.cameraControl.startFocusAndMetering(action)

                            // Fokus-Indikator animieren
                            focusTapOffset = tapOffset
                            coroutineScope.launch {
                                focusAlpha.snapTo(1f)
                                delay(1200)
                                focusAlpha.animateTo(0f, animationSpec = tween(400))
                            }
                        }
                    }
            ) {
                CameraPreviewView(previewView = previewView, modifier = Modifier.fillMaxSize())
            }

            // Tap-to-Focus Indikator – weißes Quadrat an der Tipp-Position
            focusTapOffset?.let { tapOffset ->
                val boxSizePx = with(density) { 72.dp.toPx() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (tapOffset.x - boxSizePx / 2).roundToInt(),
                                (tapOffset.y - boxSizePx / 2).roundToInt()
                            )
                        }
                        .size(72.dp)
                        .border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = focusAlpha.value),
                            shape = RoundedCornerShape(6.dp)
                        )
                )
            }

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

            // Einzelbild-Button rechts (nur wenn kein Scan läuft)
            if (!uiState.isScanning) {
                FloatingActionButton(
                    onClick = { coroutineScope.launch { capturePhoto(0) } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 300.dp, end = 16.dp),
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
                onStartScan = { viewModel.startScan { index -> capturePhoto(index) } },
                onCancelScan = viewModel::cancelScan,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
                onRotateLeft = viewModel::rotateLeft,
                onRotateRight = viewModel::rotateRight,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun CameraPermissionDialog(onRequestPermission: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kamera-Berechtigung erforderlich") },
        text = { Text("Die App ben\u00f6tigt Kamerazugriff f\u00fcr den 3D-Scan.") },
        confirmButton = { Button(onClick = onRequestPermission) { Text("Berechtigung erteilen") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun CameraPermissionDeniedScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Kamera-Berechtigung ben\u00f6tigt", color = Color.White)
            Text(
                text = "Bitte erteile die Berechtigung in den App-Einstellungen.",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp)
            )
        }
    }
}
