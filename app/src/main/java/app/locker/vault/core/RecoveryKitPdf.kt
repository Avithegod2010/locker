package app.locker.vault.core

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a one-page, printable recovery kit. Everything is generated locally into the
 * [OutputStream] chosen through the system file picker; no copy is cached by Locker.
 */
object RecoveryKitPdf {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 56f

    fun write(out: OutputStream, code: String, generatedAt: Long = System.currentTimeMillis()) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        try {
            draw(page.canvas, code, generatedAt)
            document.finishPage(page)
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun draw(canvas: Canvas, code: String, generatedAt: Long) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var y = MARGIN

        paint.color = Color.rgb(18, 24, 38)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 25f
        canvas.drawText("Locker recovery kit", MARGIN, y, paint)

        y += 20f
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 10.5f
        paint.color = Color.rgb(95, 105, 122)
        val date = SimpleDateFormat("d MMMM yyyy, h:mm a", Locale.getDefault()).format(Date(generatedAt))
        canvas.drawText("Generated $date", MARGIN, y, paint)

        y += 18f
        paint.color = Color.rgb(210, 216, 226)
        paint.strokeWidth = 1f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint)

        y += 36f
        paint.color = Color.rgb(95, 105, 122)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9.5f
        canvas.drawText("RECOVERY KEY", MARGIN, y, paint)

        // Key box. The numeric key fits on one line; legacy keys use the grouped rows for wrapping.
        val keyLines = RecoveryCodeFormat.display(code).lines().flatMap { line ->
            wrap(line, paint.apply { typeface = Typeface.MONOSPACE; textSize = 15.5f }, PAGE_WIDTH - MARGIN * 2 - 32f)
        }.ifEmpty { listOf("Recovery key unavailable") }
        val boxHeight = 24f + keyLines.size * 22f
        y += 12f
        paint.color = Color.rgb(244, 246, 250)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + boxHeight, 12f, 12f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(214, 220, 230)
        canvas.drawRoundRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + boxHeight, 12f, 12f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(18, 24, 38)
        paint.typeface = Typeface.MONOSPACE
        paint.textSize = 15.5f
        var keyY = y + 24f
        keyLines.forEach { line ->
            canvas.drawText(line, MARGIN + 16f, keyY, paint)
            keyY += 22f
        }
        y += boxHeight + 28f

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 11.5f
        paint.color = Color.rgb(45, 54, 70)
        y = paragraph(
            canvas,
            paint,
            "This key can unlock your Locker vault if you forget the vault password. " +
                "Anyone who has this page can open the vault, so treat it like a spare key: print it, " +
                "store it somewhere private, and do not keep a photo of it in cloud notes or a gallery.",
            MARGIN,
            y,
            PAGE_WIDTH - MARGIN * 2,
            17f
        )

        y += 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13.5f
        paint.color = Color.rgb(18, 24, 38)
        canvas.drawText("How to use it", MARGIN, y, paint)
        y += 26f

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 11.5f
        paint.color = Color.rgb(45, 54, 70)
        val steps = listOf(
            "1. Install Locker on a phone and open it.",
            "2. On the unlock screen, choose the recovery-key option.",
            "3. Type the 25 digits in order. Spaces are optional.",
            "4. Set a new vault password, then store this page offline again."
        )
        steps.forEach { step ->
            y = paragraph(canvas, paint, step, MARGIN + 6f, y, PAGE_WIDTH - MARGIN * 2 - 6f, 16f)
            y += 5f
        }

        y += 14f
        val warningTop = y
        val warningLines = wrap(
            "Warning: rotating the recovery key makes this page stop working immediately. " +
                "If you ever suspect this page was seen by someone else, rotate the key in Security and print a new kit.",
            paint.apply { typeface = Typeface.DEFAULT; textSize = 10.5f },
            PAGE_WIDTH - MARGIN * 2 - 28f
        )
        val warningHeight = 20f + warningLines.size * 15f
        paint.color = Color.rgb(255, 246, 232)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(MARGIN, warningTop, PAGE_WIDTH - MARGIN, warningTop + warningHeight, 10f, 10f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(238, 203, 148)
        canvas.drawRoundRect(MARGIN, warningTop, PAGE_WIDTH - MARGIN, warningTop + warningHeight, 10f, 10f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(112, 75, 20)
        var warningY = warningTop + 18f
        warningLines.forEach { line ->
            canvas.drawText(line, MARGIN + 14f, warningY, paint)
            warningY += 15f
        }

        val footerY = PAGE_HEIGHT - 42f
        paint.color = Color.rgb(210, 216, 226)
        paint.strokeWidth = 1f
        canvas.drawLine(MARGIN, footerY - 16f, PAGE_WIDTH - MARGIN, footerY - 16f, paint)
        paint.color = Color.rgb(120, 130, 146)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 9f
        canvas.drawText(
            "Locker ${app.locker.vault.BuildConfig.VERSION_NAME} · offline-only encrypted vault · no network permission",
            MARGIN,
            footerY,
            paint
        )
    }

    private fun paragraph(
        canvas: Canvas,
        paint: Paint,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        lineHeight: Float
    ): Float {
        var y = startY
        wrap(text, paint, maxWidth).forEach { line ->
            canvas.drawText(line, x, y, paint)
            y += lineHeight
        }
        return y
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        text.split(' ').forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current.clear()
                current.append(candidate)
            } else {
                if (current.isNotEmpty()) {
                    lines += current.toString()
                    current.clear()
                }
                if (paint.measureText(word) > maxWidth) {
                    // Break a very long token (for example an unspaced legacy key) by character.
                    var chunk = StringBuilder()
                    word.forEach { ch ->
                        if (paint.measureText("$chunk$ch") > maxWidth) {
                            lines += chunk.toString()
                            chunk = StringBuilder()
                        }
                        chunk.append(ch)
                    }
                    current.append(chunk)
                } else {
                    current.append(word)
                }
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }
}
