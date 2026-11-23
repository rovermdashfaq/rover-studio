package com.example.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.data.model.LayerSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class PublicQuickLink(
    val title: String,
    val url: String,
    val thumbnailUrl: String?
)

data class PublicRoverAssets(
    val layer1DataOrUrl: String? = null,
    val layer2DataOrUrl: String? = null,
    val layer3DataOrUrl: String? = null,
    val logoPngDataOrUrl: String? = null,
    val logoVideoUrl: String? = null,
    val otherLinks: List<PublicQuickLink> = emptyList()
)

class RoverPublicSyncService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun fetchOfficialRoverAssets(): Result<PublicRoverAssets> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(OFFICIAL_FIRESTORE_REST_URL)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Remote sync failed with HTTP ${response.code}")
                    )
                }
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Empty response from Rover server"))
                }
                val root = JSONObject(bodyStr)
                val fields = root.optJSONObject("fields") ?: JSONObject()

                val layer1 = fields.optJSONObject("layer1")?.optString("stringValue")?.takeIf { it.length > 50 }
                    ?.let { ensureFirestoreSafeAsset(it) }
                val layer2 = fields.optJSONObject("layer2")?.optString("stringValue")?.takeIf { it.length > 50 }
                    ?.let { ensureFirestoreSafeAsset(it) }
                val layer3 = fields.optJSONObject("layer3")?.optString("stringValue")?.takeIf { it.length > 50 }
                    ?.let { ensureFirestoreSafeAsset(it) }

                var logoPng: String? = null
                var logoVideo: String? = null
                val logoFields = fields.optJSONObject("logo")
                    ?.optJSONObject("mapValue")
                    ?.optJSONObject("fields")
                if (logoFields != null) {
                    val type = logoFields.optJSONObject("type")?.optString("stringValue")
                    val data = logoFields.optJSONObject("data")?.optString("stringValue")
                    val url = logoFields.optJSONObject("url")?.optString("stringValue")
                    if (type == "png" && !data.isNullOrBlank()) {
                        logoPng = ensureFirestoreSafeAsset(data)
                    } else if (type == "video" && !url.isNullOrBlank()) {
                        logoVideo = url
                    }
                    if (logoPng == null && !data.isNullOrBlank()) {
                        logoPng = ensureFirestoreSafeAsset(data)
                    }
                    if (logoVideo == null && !url.isNullOrBlank()) {
                        logoVideo = url
                    }
                }

                val links = mutableListOf<PublicQuickLink>()
                val linkValues = fields.optJSONObject("otherLinks")
                    ?.optJSONObject("arrayValue")
                    ?.optJSONArray("values")
                if (linkValues != null) {
                    for (i in 0 until linkValues.length()) {
                        val itemFields = linkValues.optJSONObject(i)
                            ?.optJSONObject("mapValue")
                            ?.optJSONObject("fields") ?: continue
                        val title = itemFields.optJSONObject("title")?.optString("stringValue")?.trim().orEmpty()
                        val url = itemFields.optJSONObject("url")?.optString("stringValue")?.trim().orEmpty()
                        val thumb = itemFields.optJSONObject("thumbnail")?.optString("stringValue")?.trim()
                            ?.takeIf { it.isNotEmpty() && it.length <= 500 }
                        if (title.isNotEmpty() && url.isNotEmpty()) {
                            links.add(
                                PublicQuickLink(
                                    title = title.take(120),
                                    url = url.take(500),
                                    thumbnailUrl = thumb
                                )
                            )
                        }
                    }
                }

                Result.success(
                    PublicRoverAssets(
                        layer1DataOrUrl = layer1,
                        layer2DataOrUrl = layer2,
                        layer3DataOrUrl = layer3,
                        logoPngDataOrUrl = logoPng,
                        logoVideoUrl = logoVideo,
                        otherLinks = links
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching official Rover assets: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Ensures base64 data URIs fit cleanly inside the 800,000 char Firestore field bound.
     */
    private fun ensureFirestoreSafeAsset(raw: String): String? {
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw.take(800_000)
        }
        if (raw.length <= 750_000) return raw
        return try {
            val base64Part = raw.substringAfter("base64,", raw)
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val targetW = minOf(decoded.width, 1920)
            val targetH = (decoded.height * (targetW.toFloat() / decoded.width.coerceAtLeast(1))).toInt().coerceAtLeast(1)
            val scaled = if (targetW < decoded.width) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
            scaled.recycle()
            val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            val uri = "data:image/webp;base64,$encoded"
            if (uri.length <= 780_000) uri else null
        } catch (e: Exception) {
            Log.w(TAG, "Could not compress asset for Firestore", e)
            null
        }
    }

    companion object {
        private const val TAG = "RoverPublicSync"
        private const val OFFICIAL_FIRESTORE_REST_URL =
            "https://firestore.googleapis.com/v1/projects/rover-bulletin-headlines/databases/(default)/documents/layers/bulletinLayers"
        const val DEFAULT_ROVER_LOGO_URL = "https://i.ibb.co/jPVmRjLH/1000000532.png"
        const val DEFAULT_BULLETIN_HEADER_LOGO_URL = "https://i.ibb.co/zHrqr7fp/1000019777.png"

        val DEFAULT_LINKS = listOf(
            PublicQuickLink(
                title = "Nameplate Generator (Web)",
                url = "https://rovermdashfaq.github.io/bulletin-headlines/",
                thumbnailUrl = null
            ),
            PublicQuickLink(
                title = "Developer Portfolio",
                url = "https://rovermdashfaq.github.io/portfolio/",
                thumbnailUrl = null
            )
        )
    }
}
