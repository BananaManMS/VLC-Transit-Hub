package com.example.ui.dashboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class MetroCategoryCounts(
    val obras: Int = 0,
    val servicioEspecial: Int = 0,
    val incidencias: Int = 0,
    val avisos: Int = 0,
    val meteorologica: Int = 0,
    val promocion: Int = 0,
    val otro: Int = 0
)

data class CategoryCountBadge(
    val label: String,
    val count: Int,
    val color: Color,
    val icon: ImageVector
)
