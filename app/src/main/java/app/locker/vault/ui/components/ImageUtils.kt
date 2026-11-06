package app.locker.vault.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Decodes an in-memory image with aggressive downsampling so large photos don't OOM. */
fun decodeSampledBitmap(
    bytes: ByteArray,
    maxWidth: Int,
    maxHeight: Int
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    while (largest / (sample * 2) >= maxWidth || largest / (sample * 2) >= maxHeight) {
        sample *= 2
    }
    if (largest / sample > maxOf(maxWidth, maxHeight) * 2) {
        sample *= 2
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()
}
