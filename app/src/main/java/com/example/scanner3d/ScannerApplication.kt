package com.example.scanner3d

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt-Einstiegspunkt: Triggert die Codegenerierung für Dependency Injection.
 * Muss in AndroidManifest.xml als android:name=".ScannerApplication" registriert sein.
 */
@HiltAndroidApp
class ScannerApplication : Application()
