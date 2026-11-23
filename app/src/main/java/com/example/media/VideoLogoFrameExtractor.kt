package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoLogoFrameExtractor private constructor(
    private val frames: List<Bitmap>,
    private val sampleFps: Double,
    val durationSeconds: Double
) {
    fun frameAt(elapsedSeconds: Double): Bitmap? {
        if (frames.isEmpty()) return null
        val loopTime = if (durationSeconds > 0.05) elapsedSeconds % durationSeconds else 0.0
        val idx = (loopTime * sampleFps).toInt().coerceIn(0, frames.lastIndex)
        return frames[idx]
    }

    fun release() {
        frames.forEach { if (!it.isRecycled) it.recycle() }
    }

    companion object {
        private const val TAG = "VideoLogoExtractor"

        suspend fun load(
            context: Context,
            source: String,
            targetWidth: Int,
            targetHeight: Int,
            maxSampleFrames: Int = 120
        ): VideoLogoFrameExtractor? = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                if (source.startsWith("http://") || source.startsWith("https://")) {
                    retriever.setDataSource(source, emptyMap())
                } else {
                    retriever.setDataSource(context, Uri.parse(source))
                }
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 3000L
                val effectiveDurationSec = (durationMs / 1000.0).coerceIn(0.5, 6.0)
                val sampleFps = (maxSampleFrames / effectiveDurationSec).coerceIn(12.0, 30.0)
                val totalSamples = (effectiveDurationSec * sampleFps).toInt().coerceIn(1, maxSampleFrames)

                val outFrames = ArrayList<Bitmap>(totalSamples)
                val outW = targetWidth.coerceIn(64, 640)
                val outH = targetHeight.coerceIn(64, 640)

                for (i in 0 until totalSamples) {
                    val timeUs = ((i / sampleFps) * 1_000_000.0).toLong()
                    val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: continue
                    val scaled = Bitmap.createScaledBitmap(raw, outW, outH, true)
                    if (scaled !== raw) raw.recycle()
                    outFrames.add(scaled)
                }

                if (outFrames.isEmpty()) return@withContext null
                VideoLogoFrameExtractor(outFrames, sampleFps, effectiveDurationSec)
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract video logo frames from $source: ${e.message}")
                null
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }
    }
}
