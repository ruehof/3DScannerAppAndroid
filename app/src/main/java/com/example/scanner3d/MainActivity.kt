package com.example.scanner3d

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.scanner3d.presentation.ui.screens.CameraScreen
import com.example.scanner3d.presentation.ui.screens.SettingsScreen
import com.example.scanner3d.presentation.ui.theme.Scanner3DTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Einzige Activity der App. Hostet den Compose-Navigationsgraphen.
 * @AndroidEntryPoint aktiviert Hilt-Injection.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Scanner3DTheme {
                ScannerNavGraph()
            }
        }
    }
}

@Composable
fun ScannerNavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "camera"
    ) {
        composable("camera") {
            CameraScreen(
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
