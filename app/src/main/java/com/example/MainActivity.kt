package com.example

import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.theme.VlcMetroTheme
import com.example.util.CrashCatcher

class MainActivity : ComponentActivity() {
    // App launch activity - VLC Transit
    private val dashboardViewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        com.example.util.StartupProfiler.log("MainActivity", "onCreate started")
        installSplashScreen()
        // Initialize global uncaught exception interceptor
        CrashCatcher.init(applicationContext)

        applyDeviceOrientation()
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.util.StartupProfiler.log("MainActivity", "setContent starting")
        setContent {
            val isDark by dashboardViewModel.isDarkMode.collectAsState()
            val appLanguage by dashboardViewModel.appLanguage.collectAsState()

            val locale = remember(appLanguage) {
                if (appLanguage == com.example.ui.dashboard.AppLanguage.CA) java.util.Locale("ca") else java.util.Locale("es")
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            val localizedConfiguration = remember(locale, context) {
                android.content.res.Configuration(context.resources.configuration).apply {
                    setLocale(locale)
                }
            }
            val localizedContext = remember(locale, context) {
                context.createConfigurationContext(localizedConfiguration)
            }

            androidx.compose.runtime.SideEffect {
                try {
                    java.util.Locale.setDefault(locale)
                    @Suppress("DEPRECATION")
                    val res = this@MainActivity.resources
                    val conf = res.configuration
                    if (conf.locales.get(0)?.language != locale.language) {
                        conf.setLocale(locale)
                        @Suppress("DEPRECATION")
                        res.updateConfiguration(conf, res.displayMetrics)
                    }
                } catch (_: Exception) {}
            }

            val activityResultRegistryOwner = remember(context) {
                (context as? androidx.activity.result.ActivityResultRegistryOwner)
                    ?: (this@MainActivity as androidx.activity.result.ActivityResultRegistryOwner)
            }

            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides localizedConfiguration,
                androidx.compose.ui.platform.LocalContext provides localizedContext,
                androidx.activity.compose.LocalActivityResultRegistryOwner provides activityResultRegistryOwner
            ) {
                VlcMetroTheme(darkTheme = isDark) {
                    var crashReport by remember { mutableStateOf(CrashCatcher.getCrashReport(applicationContext)) }
                    
                    DashboardScreen(viewModel = dashboardViewModel)
                
                if (crashReport != null) {
                    val currentReport = crashReport!!
                    AlertDialog(
                        onDismissRequest = { /* Prevent dismissal without action */ },
                        title = { Text(text = "Informe de Error Reciente", fontSize = 18.sp) },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "La aplicación se cerró inesperadamente en la sesión anterior. Aquí tienes el reporte técnico:",
                                    fontSize = 14.sp,
                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                )
                                Text(
                                    text = currentReport,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 250.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Crash Report", currentReport)
                                clipboard.setPrimaryClip(clip)
                            }) {
                                Text(androidx.compose.ui.res.stringResource(com.example.R.string.btn_copy_report))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                CrashCatcher.clearCrashReport(applicationContext)
                                crashReport = null
                            }) {
                                Text(androidx.compose.ui.res.stringResource(com.example.R.string.card_alert_understood))
                            }
                        }
                    )
                }
            }
        }
    }
}

    private fun applyDeviceOrientation(config: Configuration = resources.configuration) {
        val isTablet = resources.getBoolean(R.bool.allow_land_rotation) || config.smallestScreenWidthDp >= 600
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyDeviceOrientation(newConfig)
    }
}
