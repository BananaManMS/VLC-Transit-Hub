package com.example.ui.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsRailway
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Subway
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DashboardBottomNavBar(
    activeTab: DashboardTab,
    isDarkMode: Boolean,
    texts: Translation,
    onTabSelected: (DashboardTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.testTag("bottom_nav_bar"),
        containerColor = if (isDarkMode) Color(0xFF000000) else Color(0xFFFFFFFF),
        tonalElevation = 0.dp
    ) {
        val navColors = NavigationBarItemDefaults.colors(
            indicatorColor = if (isDarkMode) Color(0xFF262626) else Color(0xFFE2E8F0),
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = if (isDarkMode) Color(0xFF8E8E93) else Color(0xFF64748B),
            unselectedTextColor = if (isDarkMode) Color(0xFF8E8E93) else Color(0xFF64748B)
        )

        NavigationBarItem(
            selected = activeTab == DashboardTab.Inicio,
            onClick = { onTabSelected(DashboardTab.Inicio) },
            label = {
                Text(
                    texts.tabInicio,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (activeTab == DashboardTab.Inicio) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            icon = {
                Icon(
                    imageVector = if (activeTab == DashboardTab.Inicio) Icons.Default.Home else Icons.Outlined.Home,
                    contentDescription = texts.tabInicio
                )
            },
            colors = navColors,
            modifier = Modifier.testTag("tab_inicio")
        )

        NavigationBarItem(
            selected = activeTab == DashboardTab.Mapa,
            onClick = { onTabSelected(DashboardTab.Mapa) },
            label = {
                Text(
                    texts.tabMapa,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (activeTab == DashboardTab.Mapa) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            icon = {
                Icon(
                    imageVector = if (activeTab == DashboardTab.Mapa) Icons.Default.Map else Icons.Outlined.Map,
                    contentDescription = texts.tabMapa
                )
            },
            colors = navColors,
            modifier = Modifier.testTag("tab_mapa")
        )

        NavigationBarItem(
            selected = activeTab == DashboardTab.Bus,
            onClick = { onTabSelected(DashboardTab.Bus) },
            label = {
                Text(
                    texts.tabBus,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (activeTab == DashboardTab.Bus) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            icon = {
                Icon(
                    imageVector = if (activeTab == DashboardTab.Bus) Icons.Default.DirectionsBus else Icons.Outlined.DirectionsBus,
                    contentDescription = texts.tabBus
                )
            },
            colors = navColors,
            modifier = Modifier.testTag("tab_bus")
        )

        NavigationBarItem(
            selected = activeTab == DashboardTab.Metro,
            onClick = { onTabSelected(DashboardTab.Metro) },
            label = {
                Text(
                    texts.tabMetro,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (activeTab == DashboardTab.Metro) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            icon = {
                Icon(
                    imageVector = if (activeTab == DashboardTab.Metro) Icons.Default.Subway else Icons.Outlined.Subway,
                    contentDescription = texts.tabMetro
                )
            },
            colors = navColors,
            modifier = Modifier.testTag("tab_metro")
        )

        NavigationBarItem(
            selected = activeTab == DashboardTab.Cercanias,
            onClick = { onTabSelected(DashboardTab.Cercanias) },
            label = {
                Text(
                    texts.tabCercanias,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (activeTab == DashboardTab.Cercanias) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            icon = {
                Icon(
                    imageVector = if (activeTab == DashboardTab.Cercanias) Icons.Default.DirectionsRailway else Icons.Outlined.DirectionsRailway,
                    contentDescription = texts.tabCercanias
                )
            },
            colors = navColors,
            modifier = Modifier.testTag("tab_cercanias")
        )
    }
}
