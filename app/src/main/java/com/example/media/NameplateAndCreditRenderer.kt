package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.CreditSection
import kotlin.math.ceil
import kotlin.math.max

class NameplateRenderer(private val context: Context) {
    private val primaryTypeface: Typeface =
        ResourcesCompat.getFont(context, R.font.hind_siliguri)
            ?: ResourcesCompat.getFont(context, R.font.noto_sans_bengali)
            ?: Typeface.DEFAULT_BOLD

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(226, 232, 240)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val fallbackBadgeBitmap: Bitmap? by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.rover_bulletin_logo)
    }

    fun renderToBitmap(
        width: Int,
        height: Int,
        nameText: String,
        unitText: String,
        topBarColorInt: Int,
        logoBitmap: Bitmap? = null,
        showLogo: Boolean = false
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas, width, height, nameText, unitText, topBarColorInt, logoBitmap, showLogo)
        return bitmap
    }

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        nameText: String,
        unitText: String,
        topBarColorInt: Int,
        logoBitmap: Bitmap? = null,
        showLogo: Boolean = false
    ) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val scale = width / 1920f
        val displayName = nameText.ifBlank { "নাম ও পদবি লিখুন" }
        val displayUnit = unitText.ifBlank { "ইউনিট / গ্রুপের নাম লিখুন" }

        val nameFontSize = 52f * scale
        val unitFontSize = 42f * scale

        textPaint.typeface = Typeface.create(primaryTypeface, Typeface.BOLD)
        textPaint.textSize = nameFontSize
        val nameWidth = textPaint.measureText(displayName)

        textPaint.typeface = Typeface.create(primaryTypeface, Typeface.NORMAL)
        textPaint.textSize = unitFontSize
        val unitWidth = textPaint.measureText(displayUnit)

        val topBarY = 800f * scale
        val topBarH = 85f * scale
        val bottomBarY = 885f * scale
        val bottomBarH = 75f * scale

        val textStartX = (if (showLogo) 200f else 60f) * scale
        val topBarWidth = max(nameWidth + textStartX + 150f * scale, 700f * scale).coerceAtMost(width.toFloat())
        val bottomBarWidth = max(unitWidth + textStartX + 280f * scale, 950f * scale).coerceAtMost(width.toFloat())

        // Bottom semi-transparent dark bar (70% opacity)
        barPaint.color = Color.argb((0.7f * 255).toInt(), 0, 0, 0)
        canvas.drawRect(0f, bottomBarY, bottomBarWidth, bottomBarY + bottomBarH, barPaint)

        // Top accent bar
        barPaint.color = topBarColorInt
        canvas.drawRect(0f, topBarY, topBarWidth, topBarY + topBarH, barPaint)

        // Logo circular badge (only rendered when showLogo is enabled)
        if (showLogo) {
            val badgeCx = 100f * scale
            val badgeCy = topBarY + ((topBarH + bottomBarH) / 2f) - (5f * scale)
            val badgeR = 70f * scale

            barPaint.color = Color.WHITE
            canvas.drawCircle(badgeCx, badgeCy, badgeR, barPaint)
            strokePaint.strokeWidth = 4f * scale
            canvas.drawCircle(badgeCx, badgeCy, badgeR, strokePaint)

            val activeLogo = logoBitmap ?: fallbackBadgeBitmap
            if (activeLogo != null && !activeLogo.isRecycled) {
                val logoSize = 110f * scale
                val half = logoSize / 2f
                val rect = RectF(badgeCx - half, badgeCy - half, badgeCx + half, badgeCy + half)
                canvas.save()
                val clipPath = android.graphics.Path().apply {
                    addCircle(badgeCx, badgeCy, badgeR - 6f * scale, android.graphics.Path.Direction.CW)
                }
                canvas.clipPath(clipPath)
                canvas.drawBitmap(activeLogo, null, rect, bitmapPaint)
                canvas.restore()
            }
        }

        // Top bar name text
        textPaint.typeface = Typeface.create(primaryTypeface, Typeface.BOLD)
        textPaint.textSize = nameFontSize
        val fm1 = textPaint.fontMetrics
        val y1 = topBarY + (topBarH / 2f) - (fm1.ascent + fm1.descent) / 2f
        canvas.drawText(displayName, textStartX, y1, textPaint)

        // Bottom bar unit text
        textPaint.typeface = Typeface.create(primaryTypeface, Typeface.NORMAL)
        textPaint.textSize = unitFontSize
        val fm2 = textPaint.fontMetrics
        val y2 = bottomBarY + (bottomBarH / 2f) - (fm2.ascent + fm2.descent) / 2f
        canvas.drawText(displayUnit, textStartX, y2, textPaint)
    }
}

class CreditRenderer(context: Context) {
    private val primaryTypeface: Typeface =
        ResourcesCompat.getFont(context, R.font.kalpurush)
            ?: ResourcesCompat.getFont(context, R.font.noto_sans_bengali)
            ?: Typeface.DEFAULT_BOLD

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(primaryTypeface, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private data class DrawBlock(
        val isGap: Boolean,
        val gapPx: Float = 0f,
        val text: String = "",
        val colorInt: Int = Color.BLACK,
        val fontSizePx: Float = 40f,
        val lineSpacingPx: Float = 14f
    )

    fun renderToBitmap(
        sections: List<CreditSection>,
        baseWidth: Int = 1920,
        scale: Int = 2,
        transparentBackground: Boolean = false
    ): Bitmap {
        val canvasWidth = baseWidth * scale
        val sidePadding = 80f * scale
        val maxTextWidth = canvasWidth - (sidePadding * 2f)
        val titleLineSpacing = 18f * scale
        val nameLineSpacing = 14f * scale
        val sectionGap = 62f * scale
        val topBottomMargin = 120f * scale

        fun adjustedSize(text: String, initialSize: Long): Float {
            var fontSize = initialSize.toFloat() * scale
            paint.textSize = fontSize
            while (paint.measureText(text) > maxTextWidth && fontSize > 18f * scale) {
                fontSize -= 1f * scale
                paint.textSize = fontSize
            }
            return fontSize
        }

        val blocks = mutableListOf<DrawBlock>()
        var totalHeight = 0f

        sections.forEachIndexed { idx, sec ->
            val titleText = sec.title.ifBlank { "শিরোনাম" }
            val titleSize = adjustedSize(titleText, sec.titleSize)
            val titleColor = parseColorSafe(sec.titleColorHex, Color.rgb(220, 38, 38))
            blocks.add(DrawBlock(false, text = titleText, colorInt = titleColor, fontSizePx = titleSize, lineSpacingPx = titleLineSpacing))
            totalHeight += titleSize + titleLineSpacing

            val names = sec.parsedNames()
            val nameColor = parseColorSafe(sec.nameColorHex, Color.rgb(17, 24, 39))
            names.forEach { name ->
                val nameSize = adjustedSize(name, sec.nameSize)
                blocks.add(DrawBlock(false, text = name, colorInt = nameColor, fontSizePx = nameSize, lineSpacingPx = nameLineSpacing))
                totalHeight += nameSize + nameLineSpacing
            }

            if (idx < sections.lastIndex) {
                blocks.add(DrawBlock(isGap = true, gapPx = sectionGap))
                totalHeight += sectionGap
            }
        }

        val canvasHeight = max(600 * scale, ceil(totalHeight + topBottomMargin).toInt())
        val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (transparentBackground) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        } else {
            canvas.drawColor(Color.WHITE)
        }

        var currentY = max(40f * scale, (canvasHeight - totalHeight) / 2f)
        paint.setShadowLayer(2f * scale, 0f, 1f * scale, Color.argb(12, 0, 0, 0))

        for (block in blocks) {
            if (block.isGap) {
                currentY += block.gapPx
                continue
            }
            paint.textSize = block.fontSizePx
            paint.color = block.colorInt
            val fm = paint.fontMetrics
            val baselineY = currentY - fm.ascent
            canvas.drawText(block.text, canvasWidth / 2f, baselineY, paint)
            currentY += block.fontSizePx + block.lineSpacingPx
        }
        paint.clearShadowLayer()

        return bitmap
    }

    companion object {
        fun parseColorSafe(hex: String, fallback: Int): Int = try {
            Color.parseColor(hex.trim())
        } catch (_: Exception) {
            fallback
        }
    }
}
