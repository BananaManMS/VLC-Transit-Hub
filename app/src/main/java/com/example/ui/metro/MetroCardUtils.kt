package com.example.ui.metro

import androidx.compose.ui.graphics.Color

fun formatCardNumber(number: String): String {
    return number.chunked(4).joinToString(" ")
}

enum class CardCategory(
    val label: String,
    val orderIndex: Int
) {
    SUMA_SENCILLO("SUMA Sencillo", 1),
    SUMA_MENSUAL("SUMA Mensual", 2),
    SUMA_TSERIES("SUMA T-Series", 3),
    TUIN("TuIN", 4),
    MOBILIS("Móbilis", 5),
    OTHER("Otros", 6)
}

fun getCardColors(category: CardCategory, isFaded: Boolean, isDarkMode: Boolean): Triple<Color, Color, Color> {
    return when (category) {
        CardCategory.SUMA_SENCILLO -> {
            if (isFaded) {
                if (isDarkMode) {
                    Triple(Color(0xFF3D1F1F), Color(0xFFEF9A9A), Color(0xFFEF9A9A).copy(alpha = 0.15f))
                } else {
                    Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Color(0xFFC62828).copy(alpha = 0.12f))
                }
            } else {
                if (isDarkMode) {
                    Triple(Color(0xFFBA1A1A), Color.White, Color.White.copy(alpha = 0.2f))
                } else {
                    Triple(Color(0xFFD32F2F), Color.White, Color.White.copy(alpha = 0.2f))
                }
            }
        }
        CardCategory.SUMA_MENSUAL -> {
            if (isFaded) {
                if (isDarkMode) {
                    Triple(Color(0xFF1B2E1F), Color(0xFFA5D6A7), Color(0xFFA5D6A7).copy(alpha = 0.15f))
                } else {
                    Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Color(0xFF2E7D32).copy(alpha = 0.12f))
                }
            } else {
                if (isDarkMode) {
                    Triple(Color(0xFF2E7D32), Color.White, Color.White.copy(alpha = 0.2f))
                } else {
                    Triple(Color(0xFF388E3C), Color.White, Color.White.copy(alpha = 0.2f))
                }
            }
        }
        CardCategory.SUMA_TSERIES -> {
            if (isFaded) {
                if (isDarkMode) {
                    Triple(Color(0xFF2E1B3D), Color(0xFFE1BEE7), Color(0xFFE1BEE7).copy(alpha = 0.15f))
                } else {
                    Triple(Color(0xFFF3E5F5), Color(0xFF7B1FA2), Color(0xFF7B1FA2).copy(alpha = 0.12f))
                }
            } else {
                if (isDarkMode) {
                    Triple(Color(0xFF6A1B9A), Color.White, Color.White.copy(alpha = 0.2f))
                } else {
                    Triple(Color(0xFF7B1FA2), Color.White, Color.White.copy(alpha = 0.2f))
                }
            }
        }
        CardCategory.TUIN -> {
            if (isFaded) {
                if (isDarkMode) {
                    Triple(Color(0xFF3E200F), Color(0xFFFFCC80), Color(0xFFFFCC80).copy(alpha = 0.15f))
                } else {
                    Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Color(0xFFE65100).copy(alpha = 0.12f))
                }
            } else {
                if (isDarkMode) {
                    Triple(Color(0xFFE65100), Color.White, Color.White.copy(alpha = 0.2f))
                } else {
                    Triple(Color(0xFFF57C00), Color.White, Color.White.copy(alpha = 0.2f))
                }
            }
        }
        CardCategory.MOBILIS -> {
            if (isFaded) {
                if (isDarkMode) {
                    Triple(Color(0xFF12223D), Color(0xFF90CAF9), Color(0xFF90CAF9).copy(alpha = 0.15f))
                } else {
                    Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), Color(0xFF1565C0).copy(alpha = 0.12f))
                }
            } else {
                if (isDarkMode) {
                    Triple(Color(0xFF1565C0), Color.White, Color.White.copy(alpha = 0.2f))
                } else {
                    Triple(Color(0xFF1976D2), Color.White, Color.White.copy(alpha = 0.2f))
                }
            }
        }
        CardCategory.OTHER -> {
            if (isFaded) {
                if (isDarkMode) {
                    Triple(Color(0xFF2E2712), Color(0xFFFFE082), Color(0xFFFFE082).copy(alpha = 0.15f))
                } else {
                    Triple(Color(0xFFFFFDE7), Color(0xFFF57F17), Color(0xFFF57F17).copy(alpha = 0.12f))
                }
            } else {
                if (isDarkMode) {
                    Triple(Color(0xFFC09918), Color.Black, Color.Black.copy(alpha = 0.12f))
                } else {
                    Triple(Color(0xFFEDC037), Color(0xFF1C1B1F), Color(0xFF1C1B1F).copy(alpha = 0.12f))
                }
            }
        }
    }
}
