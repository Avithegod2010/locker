package app.locker.vault.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Fully on-device OCR used to make scanned photos and PDFs searchable. The recognized
 * text is stored inside the encrypted item payload, never in a separate index.
 */
object Ocr {
    private const val MAX_CHARS = 20_000
    private const val MAX_PDF_PAGES = 12

    suspend fun recognize(context: Context, uri: Uri, mimeType: String): String =
        withContext(Dispatchers.IO) {
            runCatching {
                if (mimeType.startsWith("image/")) {
                    recognizeImage(context, uri)
                } else if (mimeType == "application/pdf") {
                    recognizePdf(context, uri)
                } else {
                    ""
                }
            }.getOrDefault("").take(MAX_CHARS)
        }

    private suspend fun recognizeImage(context: Context, uri: Uri): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val result = suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        if (continuation.isActive) continuation.resume(text.text)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume("")
                    }
            }
            result
        } finally {
            recognizer.close()
        }
    }

    private suspend fun recognizePdf(context: Context, uri: Uri): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val output = StringBuilder()
        try {
            val readDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return ""
            readDescriptor.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pages = minOf(renderer.pageCount, MAX_PDF_PAGES)
                    for (index in 0 until pages) {
                        renderer.openPage(index).use { page ->
                            val width = 1200
                            val height = (width.toFloat() / page.width * page.height).toInt()
                                .coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val image = InputImage.fromBitmap(bitmap, 0)
                            val text = suspendCancellableCoroutine { continuation ->
                                recognizer.process(image)
                                    .addOnSuccessListener { result ->
                                        if (continuation.isActive) continuation.resume(result.text)
                                    }
                                    .addOnFailureListener {
                                        if (continuation.isActive) continuation.resume("")
                                    }
                            }
                            if (text.isNotBlank()) {
                                output.append(text).append('\n')
                            }
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            recognizer.close()
        }
        return output.toString()
    }
}
