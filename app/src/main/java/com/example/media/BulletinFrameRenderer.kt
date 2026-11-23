package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.example.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class BulletinRenderConfig(
    val title1: String,
    val title2: String,
    val startHours12: Int,
    val startMinutes: Int,
    val startSeconds: Int,
    val isPm: Boolean,
    val dateString: String,
    val totalDurationSeconds: Double,
    val scrollSpeed: Float,
    val logoWidth: Float,
    val logoHeight: Float,
    val chromaColorInt: Int
)

data class BulletinOverlayBitmaps(
    val layer1: Bitmap? = null,
    val layer2: Bitmap? = null,
    val layer3: Bitmap? = null,
    val staticLogo: Bitmap? = null
)

class BulletinFrameRenderer(context: Context) {
    private val primaryTypeface: Typeface =
        ResourcesCompat.getFont(context, R.font.kalpurush)
            ?: ResourcesCompat.getFont(context, R.font.noto_sans_bengali)
            ?: Typeface.DEFAULT_BOLD

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val title1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = Typeface.create(primaryTypeface, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private val title2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(primaryTypeface, Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
    }

    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(primaryTypeface, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val fallbackBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fallbackTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(primaryTypeface, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val destRect = RectF()

    fun drawFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        elapsedSeconds: Double,
        config: BulletinRenderConfig,
        overlays: BulletinOverlayBitmaps,
        videoLogoFrame: Bitmap? = null
    ) {
        val s = width / 1920f
        val duration = config.totalDurationSeconds.coerceAtLeast(3.0)
        val t = elapsedSeconds.coerceIn(0.0, duration)

        // 1. Chroma / Broadcast Background
        bgPaint.color = config.chromaColorInt
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Deterministic Layer Entrance & Exit Offsets (matching HTML timeline)
        var layer1X = 0f
        var layer2X = 0f
        var layer3X = 0f
        var showHeadlines = true

        if (t <= 1.1) {
            val p2 = (t / 0.5).coerceIn(0.0, 1.0)
            layer2X = (-600f * s) * (1f - easeOutCubic(p2).toFloat())

            val p1 = ((t - 0.4) / 0.4).coerceIn(0.0, 1.0)
            layer1X = (-width.toFloat()) * (1f - easeOutCubic(p1).toFloat())

            val p3 = ((t - 0.6) / 0.5).coerceIn(0.0, 1.0)
            layer3X = (600f * s) * (1f - easeOutCubic(p3).toFloat())

            showHeadlines = t >= 0.6
        } else if (t >= duration - 1.5) {
            when {
                t <= duration - 1.0 -> {
                    val p = ((t - (duration - 1.5)) / 0.5).coerceIn(0.0, 1.0)
                    layer3X = (600f * s) * easeInCubic(p).toFloat()
                    showHeadlines = true
                }
                t <= duration - 0.5 -> {
                    val p = ((t - (duration - 1.0)) / 0.5).coerceIn(0.0, 1.0)
                    layer1X = (-width.toFloat()) * easeInCubic(p).toFloat()
                    layer3X = 600f * s
                    showHeadlines = false
                }
                else -> {
                    val p = ((t - (duration - 0.5)) / 0.5).coerceIn(0.0, 1.0)
                    layer2X = (-600f * s) * easeInCubic(p).toFloat()
                    layer1X = -width.toFloat()
                    layer3X = 600f * s
                    showHeadlines = false
                }
            }
        }

        // 3. Draw Layer 1 (or clean broadcast fallback bars)
        if (overlays.layer1 != null && !overlays.layer1.isRecycled) {
            destRect.set(layer1X, 0f, layer1X + width.toFloat(), height.toFloat())
            canvas.drawBitmap(overlays.layer1, null, destRect, bitmapPaint)
        } else {
            drawFallbackLayer1(canvas, width, height, s, layer1X)
        }

        // 4. Draw Scrolling Dual Headlines
        if (showHeadlines) {
            title1Paint.textSize = 48f * s
            title2Paint.textSize = 38f * s

            val t1Text = config.title1.ifBlank { "বাংলাদেশ স্কাউটস রোভার অঞ্চলের নতুন আপডেট..." }
            val t2Text = config.title2.ifBlank { "বিস্তারিত জানতে চোখ রাখুন আমাদের পরবর্তী বুলেটিনে..." }

            val w1 = max(title1Paint.measureText(t1Text), 500f * s)
            val w2 = max(title2Paint.measureText(t2Text), 400f * s)

            val activeScrollSeconds = max(0.0, t - 0.6)
            val pxPerSec = config.scrollSpeed * 60f * s
            val span1 = (width + w1 + 100f * s)
            val span2 = (width + w2 + 100f * s)

            val x1 = width.toFloat() - ((activeScrollSeconds * pxPerSec) % span1).toFloat()
            val x2 = width.toFloat() - ((activeScrollSeconds * pxPerSec * 0.9) % span2).toFloat()

            val fm1 = title1Paint.fontMetrics
            val y1 = (985f * s) - (fm1.ascent + fm1.descent) / 2f
            canvas.drawText(t1Text, x1 + layer1X, y1, title1Paint)

            val fm2 = title2Paint.fontMetrics
            val y2 = (1055f * s) - (fm2.ascent + fm2.descent) / 2f
            canvas.drawText(t2Text, x2 + layer1X, y2, title2Paint)
        }

        // 5. Draw Layer 2 (Headline / Bulletin Badge PNG or fallback)
        if (overlays.layer2 != null && !overlays.layer2.isRecycled) {
            destRect.set(layer2X, 0f, layer2X + width.toFloat(), height.toFloat())
            canvas.drawBitmap(overlays.layer2, null, destRect, bitmapPaint)
        } else {
            drawFallbackLayer2(canvas, s, layer2X)
        }

        // 6. Draw Layer 3 (Timer Box & Date PNG or fallback)
        if (overlays.layer3 != null && !overlays.layer3.isRecycled) {
            destRect.set(layer3X, 0f, layer3X + width.toFloat(), height.toFloat())
            canvas.drawBitmap(overlays.layer3, null, destRect, bitmapPaint)
        } else {
            drawFallbackLayer3(canvas, s, layer3X)
        }

        // 7. Timer (20s) & Date (10s) Smooth Transition Cycle
        drawTimerOrDate(canvas, s, t, layer3X, config)

        // 8. Top-Right Logo (Video frame or PNG)
        val activeLogo = videoLogoFrame ?: overlays.staticLogo
        if (activeLogo != null && !activeLogo.isRecycled) {
            val lw = config.logoWidth * s
            val lh = config.logoHeight * s
            val lx = width - lw - (30f * s) + layer3X
            val ly = 30f * s
            destRect.set(lx, ly, lx + lw, ly + lh)
            canvas.drawBitmap(activeLogo, null, destRect, bitmapPaint)
        }
    }

    private fun drawTimerOrDate(
        canvas: Canvas,
        s: Float,
        elapsed: Double,
        layer3X: Float,
        config: BulletinRenderConfig
    ) {
        val cycleLen = 30.0
        val cyclePos = elapsed % cycleLen
        val isDatePhase = cyclePos >= 20.0
        val phaseTime = if (isDatePhase) cyclePos - 20.0 else cyclePos

        var alpha = 255
        var slideOffsetX = 0f
        if (phaseTime < 0.5) {
            val p = (phaseTime / 0.5).coerceIn(0.0, 1.0)
            val ease = easeOutCubic(p).toFloat()
            alpha = (ease * 255f).toInt().coerceIn(0, 255)
            slideOffsetX = (if (isDatePhase) -120f else 120f) * s * (1f - ease)
        }

        val displayText = if (!isDatePhase) {
            val baseHours24 = convert12To24(config.startHours12, config.isPm)
            val totalSec = (baseHours24 * 3600) + (config.startMinutes * 60) + config.startSeconds + elapsed.toInt()
            val totalHours = (totalSec / 3600) % 24
            var dispH = totalHours % 12
            if (dispH == 0) dispH = 12
            val dispM = (totalSec % 3600) / 60
            val dispS = totalSec % 60
            val ampm = if (totalHours >= 12) "PM" else "AM"
            "%02d:%02d:%02d %s".format(dispH, dispM, dispS, ampm)
        } else {
            config.dateString
        }

        clockPaint.textSize = (if (!isDatePhase) 34f else 36f) * s
        clockPaint.typeface = Typeface.create(primaryTypeface, Typeface.BOLD)
        clockPaint.alpha = alpha
        clockPaint.setShadowLayer(8f * s, 2f * s, 2f * s, Color.argb((alpha * 0.8f).toInt(), 0, 0, 0))

        val fm = clockPaint.fontMetrics
        val cx = (1797f * s) + layer3X + slideOffsetX
        val cy = (983f * s) - (fm.ascent + fm.descent) / 2f
        canvas.drawText(displayText, cx, cy, clockPaint)
        clockPaint.clearShadowLayer()
    }

    private fun drawFallbackLayer1(canvas: Canvas, width: Int, height: Int, s: Float, offsetX: Float) {
        // White upper ticker bar
        fallbackBadgePaint.color = Color.WHITE
        canvas.drawRect(offsetX, 948f * s, offsetX + width, 1022f * s, fallbackBadgePaint)
        // Dark lower ticker bar
        fallbackBadgePaint.color = Color.rgb(18, 22, 32)
        canvas.drawRect(offsetX, 1022f * s, offsetX + width, height.toFloat(), fallbackBadgePaint)
    }

    private fun drawFallbackLayer2(canvas: Canvas, s: Float, offsetX: Float) {
        fallbackBadgePaint.color = Color.rgb(211, 47, 47)
        canvas.drawRect(offsetX, 948f * s, offsetX + 260f * s, 1080f * s, fallbackBadgePaint)
        fallbackTextPaint.textSize = 36f * s
        val fm = fallbackTextPaint.fontMetrics
        val cy = (1014f * s) - (fm.ascent + fm.descent) / 2f
        canvas.drawText("শিরোনাম", offsetX + 130f * s, cy, fallbackTextPaint)
    }

    private fun drawFallbackLayer3(canvas: Canvas, s: Float, offsetX: Float) {
        fallbackBadgePaint.color = Color.rgb(180, 24, 34)
        canvas.drawRect(1675f * s + offsetX, 948f * s, 1920f * s + offsetX, 1022f * s, fallbackBadgePaint)
    }

    private fun convert12To24(hours12: Int, isPm: Boolean): Int {
        var h = hours12.coerceIn(1, 12)
        if (isPm && h < 12) h += 12
        else if (!isPm && h == 12) h = 0
        return h
    }

    private fun easeOutCubic(x: Double): Double = 1.0 - (1.0 - x).pow(3.0)
    private fun easeInCubic(x: Double): Double = x * x * x

    companion object {
        fun nextThursdayDate(): LocalDate {
            val today = LocalDate.now()
            val candidate = today.with(TemporalAdjusters.next(DayOfWeek.THURSDAY))
            return candidate
        }

        fun formatDate(date: LocalDate): String =
            date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
    }
}
