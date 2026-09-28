package com.example

import android.content.ClipboardManager
import android.content.Context
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
        installSplashScreen()
        // Initialize global uncaught exception interceptor
        CrashCatcher.init(applicationContext)
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDark by dashboardViewModel.isDarkMode.collectAsState()
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
                                Text("Copiar Reporte")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                CrashCatcher.clearCrashReport(applicationContext)
                                crashReport = null
                            }) {
                                Text("Entendido")
                            }
                        }
                    )
                }
            }
        }
    }
}
