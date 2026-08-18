package com.example.ui.metro

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.example.ui.dashboard.AppLanguage
import com.example.ui.dashboard.AppTexts
import com.example.ui.dashboard.Translation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle

data class DepartureUiModel(
    val id: String,
    val lineId: String,
    val destination: String,
    val colorHex: String,
    val secondsRemaining: Int,
    val isNow: Boolean,
    val timeAnnotated: AnnotatedString,
    val bottomSheetText: String,
    val isWarningColor: Boolean,
    val isSecondaryColor: Boolean,
    val shouldBlink: Boolean,
    val numericDigit: String,
    val sharedDigits: List<String>,
    val originalDeparture: RealTimeDeparture
)
