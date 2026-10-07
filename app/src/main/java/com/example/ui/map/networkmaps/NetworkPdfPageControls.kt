package com.example.ui.map.networkmaps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.dashboard.AppLanguage
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun NetworkPdfBottomBar(
    mapItem: MapPlanItem,
    title: String,
    pageCount: Int,
    currentPageIndex: Int,
    currentScale: Float,
    isDarkMode: Boolean,
    brandColor: Color,
    appLanguage: AppLanguage,
    onBack: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onSetScale: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val barBg = if (isDarkMode) Color(0xFF171717) else Color.White
    val barBorder = if (isDarkMode) Color(0xFF262C38) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFFA3A3A3) else Color(0xFF64748B)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = barBg,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, barBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Back button on the left
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDarkMode) Color(0xFF262C38) else Color(0xFFF1F5F9),
                border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF333E50) else Color(0xFFE2E8F0)),
                modifier = Modifier.size(42.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("pdf_viewer_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.btn_back),
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 2. Logo + Title in center
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (mapItem.logoDrawableRes != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDarkMode) Color(0xFF262C38) else Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF333E50) else Color(0xFFE2E8F0)),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = mapItem.logoDrawableRes!!),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = title,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (pageCount > 1) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.page_indicator_format, currentPageIndex + 1, pageCount),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 10.sp,
                            color = textSecondary
                        )
                    }
                }
            }

            // 3. Multi-page arrows (if multi-page)
            if (pageCount > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPreviousPage,
                        enabled = currentPageIndex > 0,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Pàgina anterior",
                            tint = if (currentPageIndex > 0) textPrimary else textSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onNextPage,
                        enabled = currentPageIndex < pageCount - 1,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Pàgina següent",
                            tint = if (currentPageIndex < pageCount - 1) textPrimary else textSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 4. Right side: Distinct Zoom Presets (1x, 2.5x, 5x)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TallZoomPresetPill(
                    label = "1x",
                    isSelected = currentScale <= 1.4f,
                    brandColor = brandColor,
                    isDarkMode = isDarkMode,
                    onClick = { onSetScale(1.0f) }
                )
                TallZoomPresetPill(
                    label = "2.5x",
                    isSelected = currentScale in 1.8f..3.8f,
                    brandColor = brandColor,
                    isDarkMode = isDarkMode,
                    onClick = { onSetScale(2.5f) }
                )
                TallZoomPresetPill(
                    label = "5x",
                    isSelected = currentScale >= 3.9f,
                    brandColor = brandColor,
                    isDarkMode = isDarkMode,
                    onClick = { onSetScale(5.0f) }
                )
            }
        }
    }
}

@Composable
private fun TallZoomPresetPill(
    label: String,
    isSelected: Boolean,
    brandColor: Color,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) brandColor else if (isDarkMode) Color(0xFF262C38) else Color(0xFFF1F5F9),
        border = BorderStroke(
            1.dp,
            if (isSelected) brandColor else if (isDarkMode) Color(0xFF333E50) else Color(0xFFE2E8F0)
        ),
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isSelected) Color.White else if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF475569)
            )
        }
    }
}
