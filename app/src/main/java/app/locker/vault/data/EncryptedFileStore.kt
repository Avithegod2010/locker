package app.locker.vault.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import app.locker.vault.core.Crypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

data class PickedFileInfo(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class ImportedFile(
    val sizeBytes: Long,
    val wrappedFileKey: String,
    val hasThumbnail: Boolean,
    val contentHash: String
)

/**
 * File bodies are encrypted in 128 KiB AES-256-GCM chunks with a unique key per file.
 * The per-file key is itself wrapped by the vault master key, so files can be streamed
 * without ever putting a whole video or document in memory.
 *
 * On-disk layout:
 *   "LKR1" | version (1) | nonce prefix (8) | repeated [ ciphertext length (4) | ciphertext+tag ]
 */
class EncryptedFileStore(context: Context, dirName: String = "vault") {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val vaultDir = File(appContext.filesDir, dirName).apply { mkdirs() }
    private val shareDir = File(appContext.cacheDir, "share").apply { mkdirs() }
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun describe(uri: Uri): PickedFileInfo {
        var name: String? = null
        var size = -1L
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        val mime = resolver.getType(uri) ?: guessMime(name.orEmpty())
        val displayName = name?.takeIf { it.isNotBlank() } ?: "File_${System.currentTimeMillis()}"
        return PickedFileInfo(displayName, mime, size)
    }

    suspend fun importFile(
        uri: Uri,
        itemId: String,
        mimeType: String,
        masterKey: ByteArray,
        generateThumbnail: Boolean
    ): ImportedFile = withContext(Dispatchers.IO) {
        val fileKey = Crypto.randomBytes(Crypto.MASTER_KEY_BYTES)
        val target = encryptedFile(itemId)
        val temp = File(target.parentFile, "${target.name}.tmp")
        var size = 0L
        var hash = ""
        var sanitized: File? = null
        try {
            val source: Uri = if (mimeType.startsWith("image/")) {
                val sanitizedFile = sanitizeImage(uri)
                sanitized = sanitizedFile
                sanitizedFile?.let { Uri.fromFile(it) } ?: uri
            } else {
                uri
            }
            resolver.openInputStream(source)?.use { input ->
                val result = writeEncryptedStream(
                    input = input,
                    target = temp,
                    fileKey = fileKey,
                    itemId = itemId,
                    kind = KIND_FILE
                )
                size = result.first
                hash = result.second
            } ?: throw IllegalStateException("Could not open the selected file")
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }

            var hasThumb = false
            if (generateThumbnail) {
                val video = mimeType.startsWith("video/")
                val thumbBytes = runCatching { createThumbnail(uri, video) }.getOrNull()
                if (thumbBytes != null) {
                    val thumbTarget = thumbnailFile(itemId)
                    runCatching {
                        writeEncryptedBytes(
                            thumbBytes,
                            thumbTarget,
                            fileKey,
                            itemId,
                            KIND_THUMB
                        )
                    }
                    hasThumb = thumbTarget.exists() && thumbTarget.length() > 0
                }
            }

            val wrapped = Crypto.encrypt(
                masterKey,
                fileKey,
                "locker:filekey:$itemId".toByteArray()
            )
            ImportedFile(
                sizeBytes = size,
                wrappedFileKey = Crypto.b64encode(wrapped),
                hasThumbnail = hasThumb,
                contentHash = hash
            )
        } catch (t: Throwable) {
            temp.delete()
            target.delete()
            thumbnailFile(itemId).delete()
            throw t
        } finally {
            sanitized?.delete()
            Crypto.wipe(fileKey)
        }
    }

    /**
     * Removes GPS coordinates and device-identifying EXIF tags before the bytes are
     * encrypted. The temporary file lives only for the duration of the import.
     */
    private fun sanitizeImage(uri: Uri): File? = runCatching {
        val dir = File(appContext.cacheDir, "sanitize").apply { mkdirs() }
        val temp = File(dir, "img_${System.currentTimeMillis()}.tmp")
        resolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        val exif = ExifInterface(temp.absolutePath)
        listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            "SerialNumber",
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_IMAGE_UNIQUE_ID,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED
        ).forEach { tag ->
            runCatching { exif.setAttribute(tag, null) }
        }
        runCatching { exif.saveAttributes() }
        temp
    }.getOrNull()

    suspend fun readBytes(
        masterKey: ByteArray,
        item: VaultItem,
        thumbnail: Boolean = false,
        maxBytes: Long = 96L * 1024 * 1024
    ): ByteArray? = withContext(Dispatchers.IO) {
                val target = if (thumbnail) thumbnailFile(item.id) else encryptedFile(item.id)
        if (!target.exists()) return@withContext null
        val fileKey = unwrapFileKey(masterKey, item) ?: return@withContext null
        try {
            val output = ByteArrayOutputStream(
                if (thumbnail) 64 * 1024 else minOf(target.length(), 4L * 1024 * 1024).toInt()
            )
            if (!decryptToOutput(
                    source = target,
                    output = output,
                    fileKey = fileKey,
                    itemId = item.id,
                    kind = if (thumbnail) KIND_THUMB else KIND_FILE,
                    maxBytes = maxBytes,
                    expectedSize = if (thumbnail) -1L else item.sizeBytes,
                    expectedHash = if (thumbnail) null else item.contentHash.takeIf { it.isNotBlank() }
                )
            ) {
                null
            } else {
                output.toByteArray()
            }
        } finally {
            Crypto.wipe(fileKey)
        }
    }

    /**
     * Decrypts a file into the app cache so it can be handed to an external viewer or the
     * share sheet. The cache is wiped whenever the vault locks.
     */
    suspend fun decryptToShareCache(
        masterKey: ByteArray,
        item: VaultItem
    ): File? = withContext(Dispatchers.IO) {
        val target = encryptedFile(item.id)
        if (!target.exists()) return@withContext null
        val safeName = safeFileName(item.fileName.ifBlank { item.title })
        val outputFile = File(shareDir, "${item.id}_${System.currentTimeMillis()}_$safeName").apply {
            parentFile?.mkdirs()
        }
        val fileKey = unwrapFileKey(masterKey, item) ?: return@withContext null
        try {
            val ok = outputFile.outputStream().use { output ->
                decryptToOutput(
                    source = target,
                    output = output,
                    fileKey = fileKey,
                    itemId = item.id,
                    kind = KIND_FILE,
                    maxBytes = Long.MAX_VALUE,
                    expectedSize = item.sizeBytes,
                    expectedHash = item.contentHash.takeIf { it.isNotBlank() }
                )
            }
            if (ok) {
                // The external viewer/share target gets a short-lived copy, not a permanent one.
                cleanupScope.launch {
                    delay(120_000)
                    runCatching { outputFile.delete() }
                }
                outputFile
            } else {
                outputFile.delete()
                null
            }
        } finally {
            Crypto.wipe(fileKey)
        }
    }

    suspend fun exportToStream(
        masterKey: ByteArray,
        item: VaultItem,
        output: OutputStream
    ): Boolean = withContext(Dispatchers.IO) {
        val target = encryptedFile(item.id)
        if (!target.exists()) return@withContext false
        val fileKey = unwrapFileKey(masterKey, item) ?: return@withContext false
        try {
            decryptToOutput(
                source = target,
                output = output,
                fileKey = fileKey,
                itemId = item.id,
                kind = KIND_FILE,
                maxBytes = Long.MAX_VALUE,
                expectedSize = item.sizeBytes,
                expectedHash = item.contentHash.takeIf { it.isNotBlank() }
            )
        } finally {
            Crypto.wipe(fileKey)
        }
    }

    fun cachedThumbnailPathExists(itemId: String): Boolean = thumbnailFile(itemId).exists()

    fun cachedFileExists(itemId: String): Boolean = encryptedFile(itemId).exists()

    fun rawFile(itemId: String): File = encryptedFile(itemId)

    fun rawThumbnail(itemId: String): File = thumbnailFile(itemId)

    fun writeRawFile(itemId: String, input: InputStream) {
        writeRaw(encryptedFile(itemId), input)
    }

    fun writeRawThumbnail(itemId: String, input: InputStream) {
        writeRaw(thumbnailFile(itemId), input)
    }

    private fun writeRaw(target: File, input: InputStream) {
        val temp = File(target.parentFile, "${target.name}.restore.tmp")
        temp.outputStream().use { output -> input.copyTo(output) }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    fun deleteItemFiles(itemId: String) {
        encryptedFile(itemId).delete()
        thumbnailFile(itemId).delete()
        clearShareCacheFor(itemId)
    }

    fun clearShareCacheFor(itemId: String) {
        shareDir.listFiles { file -> file.name.startsWith("${itemId}_") }?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    fun clearShareCache() {
        shareDir.deleteRecursively()
        shareDir.mkdirs()
    }

    fun clearSensitiveCaches() {
        clearShareCache()
        File(appContext.cacheDir, "camera").deleteRecursively()
        File(appContext.cacheDir, "sanitize").deleteRecursively()
        File(appContext.cacheDir, "restore").deleteRecursively()
    }

    fun wipeAll() {
        vaultDir.deleteRecursively()
        vaultDir.mkdirs()
        clearSensitiveCaches()
    }

    fun createCameraTempFile(): File {
        val dir = File(appContext.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }

    private fun encryptedFile(itemId: String) = File(vaultDir, "$itemId.bin")
    private fun thumbnailFile(itemId: String) = File(vaultDir, "$itemId.thumb")

    private fun unwrapFileKey(masterKey: ByteArray, item: VaultItem): ByteArray? {
        val wrapped = item.wrappedFileKey?.let(Crypto::b64decode) ?: return null
        return Crypto.decrypt(
            masterKey,
            wrapped,
            "locker:filekey:${item.id}".toByteArray()
        )
    }

    private fun writeEncryptedStream(
        input: InputStream,
        target: File,
        fileKey: ByteArray,
        itemId: String,
        kind: Int
    ): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val prefix = Crypto.randomBytes(8)
        val buffer = ByteArray(CHUNK_SIZE)
        var total = 0L
        var index = 0
        FileOutputStream(target).buffered().use { output ->
            output.write(MAGIC)
            output.write(VERSION)
            output.write(prefix)
            while (true) {
                var read = 0
                while (read < CHUNK_SIZE) {
                    val r = input.read(buffer, read, CHUNK_SIZE - read)
                    if (r < 0) break
                    read += r
                }
                if (read <= 0) break
                digest.update(buffer, 0, read)
                val nonce = nonce(prefix, index)
                val ciphertext = Crypto.encryptStreamChunk(
                    fileKey,
                    nonce,
                    buffer,
                    read,
                    aad(itemId, index, kind)
                )
                output.write(ByteBuffer.allocate(4).putInt(ciphertext.size).array())
                output.write(ciphertext)
                total += read
                index++
                if (read < CHUNK_SIZE) break
            }
        }
        return total to digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun writeEncryptedBytes(
        bytes: ByteArray,
        target: File,
        fileKey: ByteArray,
        itemId: String,
        kind: Int
    ) {
        bytes.inputStream().use { input ->
            writeEncryptedStream(input, target, fileKey, itemId, kind)
        }
    }

    private fun decryptToOutput(
        source: File,
        output: OutputStream,
        fileKey: ByteArray,
        itemId: String,
        kind: Int,
        maxBytes: Long,
        expectedSize: Long,
        expectedHash: String?
    ): Boolean {
        var totalBytes = 0L
        val digest = expectedHash?.let { MessageDigest.getInstance("SHA-256") }
        try {
            source.inputStream().buffered().use { input ->
                val magic = ByteArray(MAGIC.size)
                if (!readFully(input, magic) || !magic.contentEquals(MAGIC)) return false
                val version = input.read()
                if (version != VERSION) return false
                val prefix = ByteArray(8)
                if (!readFully(input, prefix)) return false
                val lengthBytes = ByteArray(4)
                var index = 0
                while (true) {
                    val first = input.read()
                    if (first < 0) break
                    lengthBytes[0] = first.toByte()
                    if (!readFully(input, lengthBytes, 1, 3)) return false
                    val length = ByteBuffer.wrap(lengthBytes).int
                    if (length <= 16 || length > CHUNK_SIZE + 16) return false
                    val ciphertext = ByteArray(length)
                    if (!readFully(input, ciphertext)) return false
                    val plaintext = Crypto.decryptStreamChunk(
                        fileKey,
                        nonce(prefix, index),
                        ciphertext,
                        aad(itemId, index, kind)
                    ) ?: return false
                    totalBytes += plaintext.size
                    if (totalBytes > maxBytes) return false
                    digest?.update(plaintext)
                    output.write(plaintext)
                    index++
                }
            }
            if (expectedSize >= 0 && totalBytes != expectedSize) return false
            if (digest != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expectedHash, ignoreCase = true)) return false
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun createThumbnail(uri: Uri, video: Boolean): ByteArray? {
        if (video) return createVideoThumbnail(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_MAX && bounds.outHeight / (sample * 2) >= THUMB_MAX) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val oriented = applyExifRotation(uri, bitmap)
        val scaled = scaleDown(oriented, THUMB_MAX)
        if (scaled !== oriented) oriented.recycle()
        return ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
            scaled.recycle()
            output.toByteArray()
        }
    }

    private fun createVideoThumbnail(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        var frame: Bitmap? = null
        return try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                retriever.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
            } ?: return null
            frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
                ?: return null
            val rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toFloatOrNull() ?: 0f
            val rotated = if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(frame!!, 0, 0, frame!!.width, frame!!.height, matrix, true)
            } else {
                frame!!
            }
            val scaled = scaleDown(rotated, THUMB_MAX)
            ByteArrayOutputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
                if (scaled !== rotated) scaled.recycle()
                if (rotated !== frame) rotated.recycle()
                output.toByteArray()
            }
        } catch (_: Exception) {
            null
        } finally {
            frame?.let { if (!it.isRecycled) it.recycle() }
            retriever.release()
        }
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleDown(bitmap: Bitmap, max: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= max) return bitmap
        val ratio = max.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun nonce(prefix: ByteArray, index: Int): ByteArray =
        prefix + ByteBuffer.allocate(4).putInt(index).array()

    private fun aad(itemId: String, index: Int, kind: Int): ByteArray =
        "locker:chunk:$kind:$index:$itemId".toByteArray()

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Boolean {
        var read = 0
        while (read < length) {
            val r = input.read(buffer, offset + read, length - read)
            if (r < 0) return false
            read += r
        }
        return true
    }

    private fun safeFileName(name: String): String {
        val base = File(name).name.ifBlank { "Locker_file" }
        return base.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120)
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heic"
            "pdf" -> "application/pdf"
            "txt", "md" -> "text/plain"
            "mp4", "mkv", "mov" -> "video/$ext"
            "mp3", "wav", "m4a" -> "audio/$ext"
            else -> "application/octet-stream"
        }
    }

    companion object {
        private val MAGIC = byteArrayOf('L'.code.toByte(), 'K'.code.toByte(), 'R'.code.toByte(), '1'.code.toByte())
        private const val VERSION: Int = 1
        private const val CHUNK_SIZE = 128 * 1024
        private const val KIND_FILE = 0
        private const val KIND_THUMB = 1
        private const val THUMB_MAX = 640
    }
}
