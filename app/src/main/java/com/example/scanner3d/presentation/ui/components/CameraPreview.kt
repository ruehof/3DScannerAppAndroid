package com.example.scanner3d.presentation.ui.components

import android.content.Context
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Composable, das einen CameraX PreviewView einbettet.
 * Nutzt AndroidView als Brücke zwischen Compose und dem View-System von CameraX.
 */
@Composable
fun CameraPreviewView(
    previewView: PreviewView,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { previewView },
        modifier = modifier,
        update = { view ->
            // PreviewView-Konfiguration bei Recomposition stabil halten
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    )
}

/** Erstellt einen konfigurierten PreviewView */
fun createPreviewView(context: Context): PreviewView {
    return PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
}
