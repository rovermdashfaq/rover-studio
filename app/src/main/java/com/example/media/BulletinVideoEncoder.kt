package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext

data class VideoExportReport(
    val file: File,
    val actualWidth: Int,
    val actualHeight: Int,
    val actualFps: Int,
    val totalFrames: Int,
    val durationSeconds: Double
)

/**
 * Deterministic 2K QHD (2560x1440) and Super High FPS (60/90/120 fps) H.264 MP4 Video Encoder.
 * Uses EGL14 + EGLExt.eglPresentationTimeANDROID so every frame has an exact monotonic PTS
 * and zero dropped frames, guaranteeing silky-smooth playback on every player.
 */
class BulletinVideoEncoder(private val context: Context) {

    suspend fun encodeBulletinToMp4(
        requestedWidth: Int,
        requestedHeight: Int,
        requestedFps: Int,
        config: BulletinRenderConfig,
        overlays: BulletinOverlayBitmaps,
        videoLogoExtractor: VideoLogoFrameExtractor?,
        onProgress: (currentFrame: Int, totalFrames: Int, message: String) -> Unit
    ): Result<VideoExportReport> = withContext(Dispatchers.Default) {
        val outputFile = File(context.cacheDir, "rover_bulletin_export_${System.currentTimeMillis()}.mp4")
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: Surface? = null
        var eglState: EglInputSurface? = null
        var frameBitmap: Bitmap? = null

        try {
            val caps = negotiateEncoderCapabilities(requestedWidth, requestedHeight, requestedFps)
            val width = caps.width
            val height = caps.height
            val fps = caps.fps
            val bitrate = caps.bitrate

            val totalFrames = (config.totalDurationSeconds * fps).toInt().coerceAtLeast(fps * 3)

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            codec.start()

            eglState = EglInputSurface(inputSurface, width, height)
            eglState.makeCurrent()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val renderer = BulletinFrameRenderer(context)
            frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val frameCanvas = Canvas(frameBitmap)

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    codec.signalEndOfInputStream()
                }
                while (true) {
                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
                    when {
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                        }
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) throw RuntimeException("Format changed twice")
                            val newFormat = codec.outputFormat
                            trackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outputIndex >= 0 -> {
                            val encodedData = codec.getOutputBuffer(outputIndex)
                                ?: throw RuntimeException("Encoder outputBuffer $outputIndex was null")
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0) {
                                if (!muxerStarted) throw RuntimeException("Muxer hasn't started")
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            }

            onProgress(0, totalFrames, "Initializing ${width}×${height} @ ${fps}fps...")

            for (frameIndex in 0 until totalFrames) {
                coroutineContext.ensureActive()

                val elapsedSeconds = frameIndex.toDouble() / fps.toDouble()
                val logoFrame = videoLogoExtractor?.frameAt(elapsedSeconds)

                renderer.drawFrame(
                    canvas = frameCanvas,
                    width = width,
                    height = height,
                    elapsedSeconds = elapsedSeconds,
                    config = config,
                    overlays = overlays,
                    videoLogoFrame = logoFrame
                )

                val ptsNs = frameIndex * 1_000_000_000L / fps.toLong()
                eglState.drawBitmapAndSwap(frameBitmap, ptsNs)
                drainEncoder(endOfStream = false)

                if (frameIndex % maxOf(1, fps / 4) == 0 || frameIndex == totalFrames - 1) {
                    onProgress(
                        frameIndex + 1,
                        totalFrames,
                        "Rendering ${width}×${height} • ${fps}fps (${frameIndex + 1}/$totalFrames)"
                    )
                }
            }

            drainEncoder(endOfStream = true)

            Result.success(
                VideoExportReport(
                    file = outputFile,
                    actualWidth = width,
                    actualHeight = height,
                    actualFps = fps,
                    totalFrames = totalFrames,
                    durationSeconds = config.totalDurationSeconds
                )
            )
        } catch (e: Throwable) {
            Log.e(TAG, "MP4 export failed", e)
            if (outputFile.exists()) outputFile.delete()
            Result.failure(Exception(e.message ?: "Video encoding failed", e))
        } finally {
            try {
                frameBitmap?.recycle()
            } catch (_: Exception) {}
            try {
                eglState?.release()
            } catch (_: Exception) {}
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}
            try {
                inputSurface?.release()
            } catch (_: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    private data class NegotiatedCaps(
        val width: Int,
        val height: Int,
        val fps: Int,
        val bitrate: Int
    )

    private fun negotiateEncoderCapabilities(
        reqW: Int,
        reqH: Int,
        reqFps: Int
    ): NegotiatedCaps {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val avcCodec = list.codecInfos.firstOrNull { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }
        }
        val videoCaps = avcCodec?.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)?.videoCapabilities

        var w = reqW
        var h = reqH
        if (videoCaps != null && !videoCaps.isSizeSupported(w, h)) {
            if (videoCaps.isSizeSupported(1920, 1080)) {
                w = 1920
                h = 1080
            } else {
                w = 1280
                h = 720
            }
        }

        var fps = reqFps.coerceIn(30, 120)
        if (videoCaps != null && !videoCaps.areSizeAndRateSupported(w, h, fps.toDouble())) {
            val fallbackRates = listOf(90, 60, 30).filter { it <= fps }
            fps = fallbackRates.firstOrNull { videoCaps.areSizeAndRateSupported(w, h, it.toDouble()) } ?: 60
        }

        val maxSupportedBitrate = videoCaps?.bitrateRange?.upper ?: 40_000_000
        val idealBitrate = when {
            w >= 2560 && fps >= 90 -> 36_000_000
            w >= 2560 -> 28_000_000
            fps >= 90 -> 24_000_000
            else -> 18_000_000
        }
        val bitrate = minOf(idealBitrate, maxSupportedBitrate)

        return NegotiatedCaps(w, h, fps, bitrate)
    }

    companion object {
        private const val TAG = "BulletinVideoEncoder"
    }
}

/**
 * Minimal OpenGL ES 2.0 + EGL14 surface bridge that uploads rendered Bitmaps and sets
 * exact deterministic presentation timestamps via EGLExt.eglPresentationTimeANDROID.
 */
private class EglInputSurface(
    private val surface: Surface,
    private val width: Int,
    private val height: Int
) {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var textureId = 0
    private var posHandle = 0
    private var texHandle = 0
    private var texInitialized = false

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(VERTICES.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(VERTICES); position(0) }

    private val texCoordBuffer: FloatBuffer = ByteBuffer.allocateDirect(TEX_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(TEX_COORDS); position(0) }

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("Unable to get EGL14 display")
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL14")
        }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)) {
            throw RuntimeException("Unable to find RGB888+recordable ES2 EGL config")
        }

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
    }

    fun makeCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
        setupGl()
    }

    private fun setupGl() {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glViewport(0, 0, width, height)
    }

    fun drawBitmapAndSwap(bitmap: Bitmap, ptsNs: Long) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        if (!texInitialized) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            texInitialized = true
        } else {
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
        }

        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142
        private val VERTICES = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
        )
        private val TEX_COORDS = floatArrayOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
        )
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }
}
