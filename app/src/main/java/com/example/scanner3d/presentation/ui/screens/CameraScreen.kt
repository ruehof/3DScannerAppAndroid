package com.example.scanner3d.presentation.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.media.ExifInterface
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Crop
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanner3d.presentation.ui.components.CameraPreviewView
import com.example.scanner3d.presentation.ui.components.CropRectOverlay
import com.example.scanner3d.presentation.ui.components.CropRectState
import com.example.scanner3d.presentation.ui.components.ScanControlPanel
import com.example.scanner3d.presentation.ui.components.createPreviewView
import com.example.scanner3d.presentation.viewmodel.CameraViewModel
import com.example.scanner3d.presentation.viewmodel.ScanState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private const val TAG = "CameraScreen"

/**
 * Repräsentiert ein verfügbares Kamera-Objektiv (Hinterkamera).
 *
 * @param cameraId       Hardware-ID der Kamera
 * @param label          Anzeige-Label, z. B. "4mm", "13mm"
 * @param cameraSelector CameraX-Selector für dieses Objektiv
 */
data class LensOption(
    val cameraId: String,
    val label: String,
    val cameraSelector: CameraSelector
)

/**
 * Haupt-Kamera-Screen des 3D-Scanners.
 *
 * Neue Features gegenüber der Basisversion:
 * - Objektivauswahl (alle Hinterkameras, sortiert nach Brennweite)
 * - Bildausschnitt-Overlay (verschieb- und größenveränderlicher Rahmen)
 * - Korrekter Scan-Timing: Stabilisierungspause → Foto (nicht umgekehrt)
 */
@OptIn(ExperimentalCamera2Interop::class)
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

    // ── CameraX-Objekte ────────────────────────────────────────────────────
    val previewView = remember { createPreviewView(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // ── Objektivauswahl ────────────────────────────────────────────────────
    var availableLenses by remember { mutableStateOf<List<LensOption>>(emptyList()) }
    var selectedLensIndex by remember { mutableStateOf(0) }

    // ── Bildausschnitt-Overlay ─────────────────────────────────────────────
    var cropEnabled by rememberSaveable { mutableStateOf(false) }
    var cropState by remember { mutableStateOf(CropRectState()) }
    // Größe der Vorschau-View in physischen Pixeln – für die Mapping-Berechnung beim Crop
    var previewViewSize by remember { mutableStateOf(IntSize.Zero) }

    // ── Tap-to-Focus ───────────────────────────────────────────────────────
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }
    val focusAlpha = remember { Animatable(0f) }

    // ── Auslösegeräusch ────────────────────────────────────────────────────
    val mediaActionSound = remember { MediaActionSound() }
    DisposableEffect(Unit) {
        mediaActionSound.load(MediaActionSound.SHUTTER_CLICK)
        onDispose { mediaActionSound.release() }
    }

    // ── Kamera-Berechtigung ────────────────────────────────────────────────
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

    // Fehlermeldungen und Scan-Fertigmeldung als Snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.scanState) {
        if (uiState.scanState is ScanState.Complete) {
            val count = (uiState.scanState as ScanState.Complete).photoCount
            snackbarHostState.showSnackbar("$count Fotos gespeichert in DCIM/3DScanner")
        }
    }

    // ── Schritt 1: Objektive aufzählen (einmalig, sobald Berechtigung vorliegt) ──
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            availableLenses = enumerateBackLenses(provider)
        }, ContextCompat.getMainExecutor(context))
    }

    // ── Schritt 2: Kamera binden, wenn Objektiv-Auswahl sich ändert ───────
    LaunchedEffect(hasCameraPermission, selectedLensIndex, availableLenses) {
        if (!hasCameraPermission) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()

            val selector = availableLenses.getOrNull(selectedLensIndex)?.cameraSelector
                ?: CameraSelector.DEFAULT_BACK_CAMERA

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageCaptureUseCase = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            imageCapture = imageCaptureUseCase

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner, selector, preview, imageCaptureUseCase
                )
            } catch (e: Exception) {
                Log.e(TAG, "CameraX Bindung fehlgeschlagen", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // ── Berechtigungsdialoge (frühzeitig verlassen) ────────────────────────
    if (showRationale && !hasCameraPermission) {
        CameraPermissionDialog(
            onRequestPermission = { showRationale = false; permissionLauncher.launch(Manifest.permission.CAMERA) },
            onDismiss = { showRationale = false; permissionDeniedPermanently = true }
        )
        return
    }
    if (permissionDeniedPermanently && !hasCameraPermission) { CameraPermissionDeniedScreen(); return }
    if (!hasCameraPermission) return

    val density = LocalDensity.current

    /**
     * Nimmt ein Foto auf, spielt optional den Auslöseton ab und schneidet
     * das Bild auf den aktuellen Crop-Ausschnitt zu (wenn aktiviert).
     */
    suspend fun capturePhoto(index: Int): Result<Uri> {
        val capture = imageCapture ?: return Result.failure(Exception("Kamera nicht bereit"))
        val deferred = CompletableDeferred<Result<Uri>>()

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

        val captureResult = deferred.await()

        // Crop anwenden, wenn aktiviert und gültige Größe bekannt
        if (captureResult.isSuccess && cropEnabled && previewViewSize != IntSize.Zero) {
            val uri = captureResult.getOrThrow()
            return cropSavedImage(context, uri, cropState, previewViewSize)
        }

        return captureResult
    }

    // ── UI ─────────────────────────────────────────────────────────────────
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
                    .onSizeChanged { previewViewSize = it }
                    .pointerInput(camera) {
                        detectTapGestures { tapOffset ->
                            val cam = camera ?: return@detectTapGestures
                            val meteringPoint = previewView.meteringPointFactory
                                .createPoint(tapOffset.x, tapOffset.y)
                            val action = FocusMeteringAction.Builder(
                                meteringPoint,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                            )
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            cam.cameraControl.startFocusAndMetering(action)

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

            // Crop-Overlay (nur wenn aktiviert)
            if (cropEnabled) {
                CropRectOverlay(
                    state = cropState,
                    onStateChange = { cropState = it },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Tap-to-Focus-Indikator
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

            // ── Obere Button-Reihe ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Crop-Toggle
                IconButton(onClick = { cropEnabled = !cropEnabled }) {
                    Icon(
                        imageVector = Icons.Default.Crop,
                        contentDescription = if (cropEnabled) "Ausschnitt deaktivieren"
                                             else "Ausschnitt aktivieren",
                        tint = if (cropEnabled) Color.Yellow.copy(alpha = 0.9f)
                               else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                // Einstellungen
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Einstellungen",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // ── Objektivauswahl (nur wenn mehrere Objektive vorhanden) ─────
            if (availableLenses.size > 1) {
                LensSelector(
                    lenses = availableLenses,
                    selectedIndex = selectedLensIndex,
                    onSelect = { selectedLensIndex = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 370.dp)
                )
            }

            // Einzelbild-Button (nur wenn kein Scan läuft)
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

// ── Objektivauswahl-Komponente ─────────────────────────────────────────────────

/**
 * Horizontale Reihe mit Buttons für jedes verfügbare Hinterkamera-Objektiv.
 * Das aktuell gewählte Objektiv ist weiß, die anderen halbtransparent.
 */
@Composable
private fun LensSelector(
    lenses: List<LensOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        lenses.forEachIndexed { index, lens ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(if (isSelected) 50.dp else 44.dp)
                    .background(
                        color = if (isSelected) Color.White.copy(alpha = 0.92f)
                                else Color.Black.copy(alpha = 0.52f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lens.label,
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = if (isSelected) 11.sp else 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ── Kamera-Objektive aufzählen ─────────────────────────────────────────────────

/**
 * Gibt alle Hinterkamera-Objektive sortiert nach Brennweite (aufsteigend) zurück.
 * Nutzt Camera2-Interop, um die Brennweite aus den Kamera-Charakteristiken zu lesen.
 * Bei Geräten mit nur einer Hinterkamera wird eine leere Liste zurückgegeben.
 */
@OptIn(ExperimentalCamera2Interop::class)
private fun enumerateBackLenses(provider: ProcessCameraProvider): List<LensOption> {
    return try {
        provider.availableCameraInfos
            .filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
            .mapNotNull { cameraInfo ->
                try {
                    val cam2 = Camera2CameraInfo.from(cameraInfo)
                    val focalLengths = cam2.getCameraCharacteristic(
                        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                    )
                    val minFocal = focalLengths?.minOrNull() ?: return@mapNotNull null
                    val cameraId = cam2.cameraId
                    Triple(cameraInfo, minFocal, cameraId)
                } catch (e: Exception) {
                    Log.w(TAG, "Brennweite für Kamera nicht lesbar: ${e.message}")
                    null
                }
            }
            .sortedBy { (_, focal, _) -> focal }
            .map { (cameraInfo, focal, cameraId) ->
                val label = if (focal < 1f) "<1mm"
                            else "${focal.toInt()}mm"
                LensOption(
                    cameraId = cameraId,
                    label = label,
                    cameraSelector = CameraSelector.Builder()
                        .addCameraFilter { infos ->
                            infos.filter { info ->
                                Camera2CameraInfo.from(info).cameraId == cameraId
                            }
                        }
                        .build()
                )
            }
    } catch (e: Exception) {
        Log.e(TAG, "Fehler bei Objektiv-Aufzählung: ${e.message}", e)
        emptyList()
    }
}

// ── Bild-Crop nach der Aufnahme ────────────────────────────────────────────────

/**
 * Lädt das gespeicherte JPEG, berechnet die Schnittkoordinaten unter Berücksichtigung
 * der EXIF-Rotation und des FILL_CENTER-Skalierungsverfahrens der Vorschau,
 * schneidet das Bild per BitmapRegionDecoder aus und speichert es zurück.
 *
 * Koordinatenmapping:
 *   scale = max(viewW / visualW, viewH / visualH)   [FILL_CENTER füllt die View]
 *   offsetX = (visualW * scale - viewW) / 2          [überstehendes Bild links/rechts]
 *   imageX  = (previewX * scale + offsetX) / scale   vereinfacht zu previewX + offsetX/scale
 */
private suspend fun cropSavedImage(
    context: Context,
    uri: Uri,
    cropRect: CropRectState,
    previewSize: IntSize
): Result<Uri> = withContext(Dispatchers.IO) {
    try {
        // 1. EXIF-Rotation lesen
        val exifRotation: Int = context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else                                 -> 0
            }
        } ?: 0

        // 2. Rohe Bildgröße (vor EXIF-Rotation) ermitteln
        val rawW: Int
        val rawH: Int
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, opts)
            rawW = opts.outWidth
            rawH = opts.outHeight
        } ?: return@withContext Result.failure(Exception("Bild nicht lesbar"))

        if (rawW <= 0 || rawH <= 0) return@withContext Result.failure(Exception("Ungültige Bildgröße"))

        // 3. Visuelle Dimensionen nach EXIF-Drehung
        val visualW = if (exifRotation == 90 || exifRotation == 270) rawH else rawW
        val visualH = if (exifRotation == 90 || exifRotation == 270) rawW else rawH

        // 4. FILL_CENTER: Skalierungsfaktor und Überhang berechnen
        //    scale = max(viewW/visualW, viewH/visualH) → Bild füllt die View, kann überstehen
        val scaleW = previewSize.width.toFloat()  / visualW
        val scaleH = previewSize.height.toFloat() / visualH
        val scale  = maxOf(scaleW, scaleH)
        // Überhang in Bildpixeln (halbiert, weil zentriert)
        val offsetX = ((visualW * scale - previewSize.width)  / 2f) / scale
        val offsetY = ((visualH * scale - previewSize.height) / 2f) / scale

        // 5. Crop-Koordinaten in visuellen Bildpixeln
        val vL = (cropRect.left   * previewSize.width  / scale + offsetX).toInt().coerceIn(0, visualW)
        val vT = (cropRect.top    * previewSize.height / scale + offsetY).toInt().coerceIn(0, visualH)
        val vR = (cropRect.right  * previewSize.width  / scale + offsetX).toInt().coerceIn(0, visualW)
        val vB = (cropRect.bottom * previewSize.height / scale + offsetY).toInt().coerceIn(0, visualH)
        if (vR <= vL || vB <= vT) return@withContext Result.failure(Exception("Ungültiger Ausschnitt"))

        // 6. Visuelle Crop-Region in rohe Sensor-Koordinaten umrechnen (EXIF-Rotation rückgängig)
        //    Formeln für Rechtecke (end-exclusive) pro Rotation:
        //    0°  → (vL, vT, vR, vB)
        //    90° → (vT, rawH-vR, vB, rawH-vL)
        //    180°→ (rawW-vR, rawH-vB, rawW-vL, rawH-vT)
        //    270°→ (rawW-vB, vL, rawW-vT, vR)
        val rawRegion = when (exifRotation) {
            0   -> android.graphics.Rect(vL, vT, vR, vB)
            90  -> android.graphics.Rect(vT, rawH - vR, vB, rawH - vL)
            180 -> android.graphics.Rect(rawW - vR, rawH - vB, rawW - vL, rawH - vT)
            270 -> android.graphics.Rect(rawW - vB, vL, rawW - vT, vR)
            else -> android.graphics.Rect(vL, vT, vR, vB)
        }

        // 7. Nur die Crop-Region dekodieren (speichereffizient)
        val rawCrop: Bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            @Suppress("DEPRECATION")
            val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(stream)
            } else {
                BitmapRegionDecoder.newInstance(stream, false)
            }
            decoder?.decodeRegion(rawRegion, null).also { decoder?.recycle() }
        } ?: return@withContext Result.failure(Exception("BitmapRegionDecoder fehlgeschlagen"))

        // 8. Rohen Ausschnitt in die visuelle Ausrichtung drehen
        val finalBitmap = if (exifRotation != 0) {
            val matrix = Matrix().apply { postRotate(exifRotation.toFloat()) }
            val rotated = Bitmap.createBitmap(rawCrop, 0, 0, rawCrop.width, rawCrop.height, matrix, true)
            rawCrop.recycle()
            rotated
        } else rawCrop

        // 9. Gecroptes Bild zurückschreiben (überschreiben)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        } ?: return@withContext Result.failure(Exception("Ausgabedatei nicht schreibbar"))

        finalBitmap.recycle()
        Result.success(uri)
    } catch (e: Exception) {
        Log.e(TAG, "Crop-Fehler: ${e.message}", e)
        Result.failure(e)
    }
}

// ── Berechtigungsdialoge ───────────────────────────────────────────────────────

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
