package com.example.ui.metro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage

@Composable
fun AforoBloqueadoCard(
    aforo: AforoBloqueado,
    appLanguage: AppLanguage,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val timeSpan = aforo.getFormattedTimeSpan()
    if (!aforo.activo && timeSpan.isNullOrEmpty()) return

    val containerColor = if (aforo.activo) {
        if (isDarkMode) Color(0xFF450A0A) else Color(0xFFFEF2F2)
    } else {
        if (isDarkMode) Color(0xFF451A03) else Color(0xFFFFFBEE)
    }

    val contentColor = if (aforo.activo) {
        if (isDarkMode) Color(0xFFFECACA) else Color(0xFF991B1B)
    } else {
        if (isDarkMode) Color(0xFFFDE68A) else Color(0xFF92400E)
    }

    val icon = if (aforo.activo) Icons.Default.Block else Icons.Default.Warning

    val titleText = if (aforo.activo) {
        if (appLanguage == AppLanguage.CA) "Accés a andanes tancat per aforament" else "Acceso a andenes cerrado por aforo"
    } else {
        if (appLanguage == AppLanguage.CA) "Avís d'aforament programat" else "Aviso de aforo programado"
    }

    val bodyText = if (aforo.activo) {
        if (appLanguage == AppLanguage.CA) {
            if (timeSpan != null) "Tancat de $timeSpan. L'accés a les instal·lacions d'aquesta estació es troba restringit."
            else "L'accés a les instal·lacions d'aquesta estació es troba restringit."
        } else {
            if (timeSpan != null) "Cerrado de $timeSpan. El acceso a las instalaciones de esta estación se encuentra restringido."
            else "El acceso a las instalaciones de esta estación se encuentra restringido."
        }
    } else {
        if (appLanguage == AppLanguage.CA) {
            "Accés restringit previst: $timeSpan."
        } else {
            "Acceso restringido previsto: $timeSpan."
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = titleText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = bodyText,
                    fontSize = 13.sp,
                    color = contentColor.copy(alpha = 0.9f),
                    lineHeight = 17.sp
                )
            }
        }
    }
}
