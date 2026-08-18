package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.repository.renfe.RenfeRepository
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.map.components.MetroMapOverlayLoader
import com.example.worker.ScheduleUpdateWorker
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !viewModel.isUiReady.value }
        
        // Read saved language asynchronously to avoid blocking the main thread
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val savedLang = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    db.preferenceDao().getPreference("app_language")?.value ?: "CA"
                }
                val langCode = if (savedLang == "ES") "es" else "ca"
                val currentLocales = AppCompatDelegate.getApplicationLocales()
                if (currentLocales.isEmpty || currentLocales.get(0)?.language != langCode) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langCode))
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error setting locale asynchronously", e)
            }
        }

        // On-app-open sync
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val renfeRepository = RenfeRepository(applicationContext, db)
                renfeRepository.syncScheduleFromRemoteIfNeeded()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error syncing schedule on open", e)
            }
        }

        // Load Metrovalencia and Cercanías lines GeoJSON on startup
        MetroMapOverlayLoader.loadMetroLines(applicationContext, lifecycleScope)
        MetroMapOverlayLoader.loadCercaniasLines(applicationContext, lifecycleScope)

        // OSMDroid MapView LOD Zoom listener reference:
        // If zoom is < 12.0, the loader swaps overlays to the low-resolution (simplified) pre-computed dataset.
        // If zoom is >= 12.0, it commutes back to high-resolution.
        // Precomputed zoom categories are set for < 13.5 (Far), 13.5 to 15.9 (Medium), and >= 16.0 (Close).
        // Note: Due to Jetpack Compose, MapView is nested and its MapListener is initialized in OsmdroidMapView.kt,
        // ensuring 100% non-blocking 60fps rendering without executing any math on the UI thread.
        
        setupDailyScheduleSync()

        // Lock orientation to portrait on mobile devices, but allow free rotation on tablets
        val allowLandRotation = resources.getBoolean(R.bool.allow_land_rotation)
        if (!allowLandRotation) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        
        super.onCreate(savedInstanceState)
        handleTransferRiskIntent(intent)
        enableEdgeToEdge()
        setContent {
            DashboardScreen(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTransferRiskIntent(intent)
    }

    private fun handleTransferRiskIntent(intent: android.content.Intent?) {
        if (intent?.action == com.example.service.ActiveTripTrackingService.ACTION_SHOW_RECALCULATE_DIALOG ||
            intent?.getBooleanExtra("SHOW_TRANSFER_DIALOG", false) == true) {
            viewModel.triggerTransferRiskDialog()
        }
    }

    private fun setupDailyScheduleSync() {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()
        
        // Set execution around 02:00 AM
        dueDate.set(Calendar.HOUR_OF_DAY, 2)
        dueDate.set(Calendar.MINUTE, 0)
        dueDate.set(Calendar.SECOND, 0)
        
        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }
        
        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val dailyWorkRequest = PeriodicWorkRequestBuilder<ScheduleUpdateWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CercaniasScheduleSync",
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyWorkRequest
        )
    }
}
