package com.example.ui.map.networkmaps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import java.io.File
import kotlin.math.max

data class PdfPageBaseResult(
    val bitmap: Bitmap,
    val pageCount: Int,
    val pdfWidth: Int,
    val pdfHeight: Int
)

object NetworkPdfRendererHelper {

    fun renderBasePage(
        file: File,
        pageIndex: Int,
        targetDimension: Float = 2600f
    ): PdfPageBaseResult {
        if (!file.exists() || file.length() < 100) {
            throw IllegalStateException("El archivo del plano no está disponible.")
        }

        // Support for image files (.png / .jpg)
        if (file.name.endsWith(".png", ignoreCase = true) || file.name.endsWith(".jpg", ignoreCase = true)) {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, options)
                ?: throw IllegalStateException("No se pudo procesar la imagen del intercambiador.")
            return PdfPageBaseResult(
                bitmap = bmp,
                pageCount = 1,
                pdfWidth = bmp.width,
                pdfHeight = bmp.height
            )
        }

        // PDF rendering
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val count = renderer.pageCount
        val validIndex = pageIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
        val page = renderer.openPage(validIndex)

        val w = page.width
        val h = page.height
        val pageMax = max(w, h).toFloat().coerceAtLeast(1f)
        val scaleFactor = targetDimension / pageMax

        val bmpWidth = (w * scaleFactor).toInt().coerceIn(1, 2800)
        val bmpHeight = (h * scaleFactor).toInt().coerceIn(1, 2800)

        val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)

        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        pfd.close()

        return PdfPageBaseResult(
            bitmap = bmp,
            pageCount = count,
            pdfWidth = w,
            pdfHeight = h
        )
    }

    fun renderDetailTile(
        file: File,
        pageIndex: Int,
        scale: Float,
        offset: Offset,
        baseFit: Float,
        pdfWidth: Int,
        pdfHeight: Int,
        containerWidth: Float,
        containerHeight: Float
    ): Bitmap? {
        if (!file.exists()) return null

        // If it's already an image file, the base bitmap is rendered natively
        if (file.name.endsWith(".png", ignoreCase = true) || file.name.endsWith(".jpg", ignoreCase = true)) {
            return null
        }

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val validIndex = pageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
        val page = renderer.openPage(validIndex)

        val currentTotalScale = baseFit * scale
        val tx = containerWidth / 2f + offset.x - (pdfWidth / 2f) * currentTotalScale
        val ty = containerHeight / 2f + offset.y - (pdfHeight / 2f) * currentTotalScale

        val matrix = Matrix().apply {
            postScale(currentTotalScale, currentTotalScale)
            postTranslate(tx, ty)
        }

        val targetW = containerWidth.toInt().coerceIn(1, 2600)
        val targetH = containerHeight.toInt().coerceIn(1, 2600)
        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val pageRect = RectF(tx, ty, tx + pdfWidth * currentTotalScale, ty + pdfHeight * currentTotalScale)
        val whitePaint = Paint().apply { color = android.graphics.Color.WHITE }
        canvas.drawRect(pageRect, whitePaint)

        page.render(bmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        pfd.close()

        return bmp
    }
}
