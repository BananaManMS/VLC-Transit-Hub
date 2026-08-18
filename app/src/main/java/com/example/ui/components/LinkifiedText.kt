package com.example.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

@Composable
fun LinkifiedText(
    text: String,
    textColor: Color,
    fontSize: TextUnit = 13.sp,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val urlPattern = Pattern.compile(
        "(?:https?|ftp)://[^\\s/$.?#].[^\\s]*",
        Pattern.CASE_INSENSITIVE
    )
    val matcher = urlPattern.matcher(text)
    
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val url = matcher.group()
            
            // Append non-link text
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }
            
            // Append link text
            pushStringAnnotation(tag = "URL", annotation = url)
            withStyle(
                style = SpanStyle(
                    color = Color(0xFF4F8CFF),
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium
                )
            ) {
                append(url)
            }
            pop()
            
            lastIndex = end
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
    
    ClickableText(
        text = annotatedString,
        style = TextStyle(
            color = textColor,
            fontSize = fontSize
        ),
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        uriHandler.openUri(annotation.item)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
        }
    )
}
