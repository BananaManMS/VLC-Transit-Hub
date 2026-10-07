package com.example.ui.dashboard.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.DashboardTab

@Composable
fun QuickTransportModesRow(
    appLanguage: AppLanguage,
    onSelectMode: (DashboardTab, Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransportModeButton(
            iconDrawable = R.drawable.logo_metrovalencia,
            label = stringResource(R.string.tab_metro),
            color = Color(0xFFEF4444),
            onClick = { onSelectMode(DashboardTab.Metro, 0) },
            modifier = Modifier.weight(1f)
        )
        TransportModeButton(
            iconDrawable = R.drawable.logo_emt_valencia,
            label = "EMT",
            color = Color(0xFF0284C7),
            onClick = { onSelectMode(DashboardTab.Bus, 0) },
            modifier = Modifier.weight(1f)
        )
        TransportModeButton(
            iconDrawable = R.drawable.logo_cercanias,
            label = stringResource(R.string.tab_cercanias),
            color = Color(0xFF702B7B),
            onClick = { onSelectMode(DashboardTab.Cercanias, 0) },
            modifier = Modifier.weight(1f)
        )
        TransportModeButton(
            iconDrawable = R.drawable.logo_metrobus,
            label = "Metrobús",
            color = com.example.util.MetrobusLineColorResolver.BRAND_COLOR,
            onClick = { onSelectMode(DashboardTab.Bus, 1) },
            modifier = Modifier.weight(1f)
        )
        TransportModeButton(
            icon = Icons.Default.PedalBike,
            label = "Valenbisi",
            color = Color(0xFF10B981),
            onClick = { onSelectMode(DashboardTab.Bus, 2) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TransportModeButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    @DrawableRes iconDrawable: Int? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (iconDrawable != null) {
                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = iconDrawable),
                        contentDescription = label,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
