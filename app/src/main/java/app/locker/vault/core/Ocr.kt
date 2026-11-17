package app.locker.vault.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.locker.vault.data.PageText
import app.locker.vault.data.PdfIndex
import app.locker.vault.data.PdfTextIndex
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
    private const val MAX_CHARS = PdfTextIndex.MAX_CHARS
    private const val MAX_PDF_PAGES = PdfTextIndex.DEFAULT_MAX_PAGES

    suspend fun recognize(context: Context, uri: Uri, mimeType: String): String =
        withContext(Dispatchers.IO) {
            runCatching {
                if (mimeType.startsWith("image/")) {
                    recognizeImage(context, uri)
                } else if (mimeType == "application/pdf") {
                    indexPdf(context, uri, MAX_PDF_PAGES).text
                } else {
                    ""
                }
            }.getOrDefault("").take(MAX_CHARS)
        }

    /**
     * Indexes up to [maxPages] pages of a PDF and returns the joined, capped text together with
     * the number of pages that actually produced text. Used both at import time and by the
     * viewer's "make searchable" action for documents imported before OCR existed.
     */
    suspend fun indexPdf(
        context: Context,
        uri: Uri,
        maxPages: Int = PdfTextIndex.DEFAULT_MAX_PAGES
    ): PdfIndex = withContext(Dispatchers.IO) {
        runCatching { recognizePdf(context, uri, maxPages) }
            .getOrDefault(PdfIndex("", 0))
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

    private suspend fun recognizePdf(context: Context, uri: Uri, maxPages: Int): PdfIndex {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val pages = mutableListOf<PageText>()
        try {
            val readDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return PdfIndex("", 0)
            readDescriptor.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val limit = PdfTextIndex.pageLimit(renderer.pageCount, maxPages)
                    for (index in 0 until limit) {
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
                                pages += PageText(page = index + 1, text = text)
                            }
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            recognizer.close()
        }
        return PdfTextIndex.merge(pages)
    }
}
