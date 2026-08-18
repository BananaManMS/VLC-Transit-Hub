package com.example.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// FULL-FEATURED POPUP DIALOG TO ADD CUSTOM EVENTS OR TASKS
@Composable
fun AddCalendarItemDialog(
    onDismiss: () -> Unit,
    onAddEvent: (String, String, Int, Int, String) -> Unit,
    onAddTask: (String, String, Int, String) -> Unit
) {
    var isEvent by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    
    // Config offsets
    var hourOffset by remember { mutableStateOf(2) } // default starts in 2 hours
    var durationHours by remember { mutableStateOf(1) } // default 1 hour duration
    
    // Selected Color tag
    val colors = listOf(
        "#4285F4", // Google Blue
        "#EA4335", // Google Red
        "#FBBC05", // Google Yellow
        "#34A853", // Google Green
        "#8B5CF6"  // Purple
    )
    var selectedColor by remember { mutableStateOf(colors.first()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(360.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Añadir evento de calendario",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Title Input
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("item_title_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description Input
                TextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("item_desc_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isEvent) {
                    // Hour offset and duration selections
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Starts in (hours):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { if (hourOffset > 0) hourOffset-- }) { Text("-") }
                                Text("$hourOffset", fontWeight = FontWeight.Bold)
                                TextButton(onClick = { hourOffset++ }) { Text("+") }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Duration (hours):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { if (durationHours > 1) durationHours-- }) { Text("-") }
                                Text("$durationHours", fontWeight = FontWeight.Bold)
                                TextButton(onClick = { durationHours++ }) { Text("+") }
                            }
                        }
                    }
                } else {
                    // Task Due offset
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Due in (hours):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { if (hourOffset > 0) hourOffset-- }) { Text("-") }
                            Text("$hourOffset", fontWeight = FontWeight.Bold)
                            TextButton(onClick = { hourOffset++ }) { Text("+") }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Color Tag Picker
                Text("Select Tag Color:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                val parsedColors = remember {
                    colors.map { it to Color(it.toColorInt()) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    parsedColors.forEach { (colStr, colColor) ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colColor)
                                .clickable { selectedColor = colStr }
                                .padding(2.dp)
                        ) {
                            if (selectedColor == colStr) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                if (isEvent) {
                                    onAddEvent(title, desc, hourOffset, durationHours, selectedColor)
                                } else {
                                    onAddTask(title, desc, hourOffset, selectedColor)
                                }
                            }
                        },
                        modifier = Modifier.testTag("dialog_confirm_button")
                    ) {
                        Text("Add Item")
                    }
                }
            }
        }
    }
}
