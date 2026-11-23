package com.example.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class SavedMediaResult(
    val galleryUri: Uri,
    val shareUri: Uri,
    val fileName: String,
    val sizeBytes: Long
)

object MediaExportHelper {
    private const val TAG = "MediaExportHelper"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    suspend fun saveBitmapAsPng(
        context: Context,
        bitmap: Bitmap,
        fileNamePrefix: String
    ): Result<SavedMediaResult> = withContext(Dispatchers.IO) {
        try {
            val safeName = fileNamePrefix.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "Rover_Export" }
            val fileName = "${safeName}_${System.currentTimeMillis()}.png"

            // Always create a shareable cache copy for FileProvider
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cacheFile = File(exportsDir, fileName)
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val fileSize = cacheFile.length()
            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            // Save to MediaStore Pictures/RoverStudio
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RoverStudio")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val galleryUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext Result.success(
                    SavedMediaResult(shareUri, shareUri, fileName, fileSize)
                )

            resolver.openOutputStream(galleryUri)?.use { out ->
                cacheFile.inputStream().use { input -> input.copyTo(out) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(galleryUri, contentValues, null, null)
            }

            Result.success(SavedMediaResult(galleryUri, shareUri, fileName, fileSize))
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving PNG", e)
            Result.failure(e)
        }
    }

    suspend fun saveMp4ToGallery(
        context: Context,
        sourceFile: File,
        fileNamePrefix: String
    ): Result<SavedMediaResult> = withContext(Dispatchers.IO) {
        try {
            val safeName = fileNamePrefix.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "Rover_Bulletin" }
            val fileName = "${safeName}_${System.currentTimeMillis()}.mp4"

            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cacheFile = File(exportsDir, fileName)
            sourceFile.copyTo(cacheFile, overwrite = true)
            val fileSize = cacheFile.length()

            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/RoverStudio")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val galleryUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext Result.success(
                    SavedMediaResult(shareUri, shareUri, fileName, fileSize)
                )

            resolver.openOutputStream(galleryUri)?.use { out ->
                cacheFile.inputStream().use { input -> input.copyTo(out) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(galleryUri, contentValues, null, null)
            }

            Result.success(SavedMediaResult(galleryUri, shareUri, fileName, fileSize))
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving MP4", e)
            Result.failure(e)
        }
    }

    suspend fun decodeBitmapFromSource(
        context: Context,
        source: String,
        maxDimension: Int = 2560
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bytes: ByteArray = when {
                source.startsWith("data:image", ignoreCase = true) ||
                    (source.length > 200 && !source.startsWith("http") && !source.startsWith("content://") && !source.startsWith("file://")) -> {
                    val b64 = source.substringAfter("base64,", source)
                    Base64.decode(b64, Base64.DEFAULT)
                }
                source.startsWith("http://") || source.startsWith("https://") -> {
                    val req = Request.Builder().url(source).get().build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext null
                        resp.body?.bytes() ?: return@withContext null
                    }
                }
                source.startsWith("content://") || source.startsWith("file://") -> {
                    val uri = Uri.parse(source)
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@withContext null
                }
                else -> return@withContext null
            }

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            var sampleSize = 1
            while ((boundsOpts.outWidth / sampleSize) > maxDimension || (boundsOpts.outHeight / sampleSize) > maxDimension) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode bitmap from source: ${e.message}")
            null
        }
    }

    suspend fun compressImageUriToDataUri(
        context: Context,
        uri: Uri,
        maxWidth: Int = 1920
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            val targetW = minOf(decoded.width, maxWidth)
            val targetH = (decoded.height * (targetW.toFloat() / decoded.width.coerceAtLeast(1))).toInt().coerceAtLeast(1)
            val scaled = if (targetW < decoded.width) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }
            val out = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            scaled.compress(format, 88, out)
            scaled.recycle()
            val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            val dataUri = "data:image/webp;base64,$encoded"
            if (dataUri.length <= 780_000) dataUri else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compress URI to data URI", e)
            null
        }
    }

    fun shareMediaUri(context: Context, uri: Uri, mimeType: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
