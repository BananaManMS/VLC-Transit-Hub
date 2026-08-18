package com.example.ui.routing.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.routing.DepartureType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScheduleDialog(
    currentDepartureType: DepartureType,
    currentTime: String?,
    appLanguage: AppLanguage = AppLanguage.CA,
    onDismiss: () -> Unit,
    onConfirm: (DepartureType, String?) -> Unit
) {
    var selectedType by remember { mutableStateOf(currentDepartureType) }
    
    val cal = Calendar.getInstance()
    val initialHour = currentTime?.substringBefore(":")?.toIntOrNull() ?: cal.get(Calendar.HOUR_OF_DAY)
    val initialMinute = currentTime?.substringAfter(":")?.toIntOrNull() ?: cal.get(Calendar.MINUTE)
    
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (appLanguage == AppLanguage.ES) "Horario del trayecto" else "Horari del trajecte",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Radio options
                DepartureType.values().forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = type }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (appLanguage == AppLanguage.ES) type.labelEs else type.labelCa,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (selectedType != DepartureType.LEAVE_NOW) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TimePicker(state = timePickerState)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val formattedTime = if (selectedType == DepartureType.LEAVE_NOW) {
                        null
                    } else {
                        String.format(Locale.ROOT, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                    }
                    onConfirm(selectedType, formattedTime)
                }
            ) {
                Text(if (appLanguage == AppLanguage.ES) "Aceptar" else "Acceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (appLanguage == AppLanguage.ES) "Cancelar" else "Cancel·lar")
            }
        }
    )
}
