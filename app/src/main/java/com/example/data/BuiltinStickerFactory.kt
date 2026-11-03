package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import com.example.model.StickerItem
import com.example.model.StickerSource
import com.example.video.AnimatedStickerDecoder
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object BuiltinStickerFactory {

    private data class StickerTemplate(
        val id: String,
        val titleAr: String,
        val badgeTextAr: String,
        val symbolText: String,
        val primaryColor: Int,
        val secondaryColor: Int,
        val accentColor: Int
    )

    private val templates = listOf(
        StickerTemplate(
            id = "anim_sticker_fire_hype",
            titleAr = "ملصق حماس ناري متحرك",
            badgeTextAr = "عاش يا بطل!",
            symbolText = "🔥",
            primaryColor = Color.parseColor("#FF512F"),
            secondaryColor = Color.parseColor("#F09819"),
            accentColor = Color.parseColor("#FFF176")
        ),
        StickerTemplate(
            id = "anim_sticker_laugh_joy",
            titleAr = "ملصق ضحكة متحرك",
            badgeTextAr = "ههههه رهيييب",
            symbolText = "😂",
            primaryColor = Color.parseColor("#00A884"),
            secondaryColor = Color.parseColor("#25D366"),
            accentColor = Color.parseColor("#FFFFFF")
        ),
        StickerTemplate(
            id = "anim_sticker_neon_heart",
            titleAr = "ملصق قلب نابض متحرك",
            badgeTextAr = "يسعد قلبك",
            symbolText = "❤️",
            primaryColor = Color.parseColor("#EC008C"),
            secondaryColor = Color.parseColor("#FC6767"),
            accentColor = Color.parseColor("#FFD1DC")
        ),
        StickerTemplate(
            id = "anim_sticker_golden_cup",
            titleAr = "ملصق كأس الصدارة متحرك",
            badgeTextAr = "رقم واحد #1",
            symbolText = "🏆",
            primaryColor = Color.parseColor("#F7971E"),
            secondaryColor = Color.parseColor("#FFD200"),
            accentColor = Color.parseColor("#FFF9C4")
        ),
        StickerTemplate(
            id = "anim_sticker_coffee_morning",
            titleAr = "ملصق قهوة الصباح متحرك",
            badgeTextAr = "صباح الروقان",
            symbolText = "☕",
            primaryColor = Color.parseColor("#603813"),
            secondaryColor = Color.parseColor("#B29F94"),
            accentColor = Color.parseColor("#FFF8E1")
        ),
        StickerTemplate(
            id = "anim_sticker_party_congrats",
            titleAr = "ملصق ألف مبروك متحرك",
            badgeTextAr = "ألف مبروك!",
            symbolText = "🎉",
            primaryColor = Color.parseColor("#7F00FF"),
            secondaryColor = Color.parseColor("#E100FF"),
            accentColor = Color.parseColor("#80D8FF")
        ),
        StickerTemplate(
            id = "anim_sticker_super_like",
            titleAr = "ملصق إعجاب متحرك",
            badgeTextAr = "كفو والله",
            symbolText = "👍",
            primaryColor = Color.parseColor("#0083B0"),
            secondaryColor = Color.parseColor("#00B4DB"),
            accentColor = Color.parseColor("#E0F7FA")
        ),
        StickerTemplate(
            id = "anim_sticker_star_magic",
            titleAr = "ملصق نجوم متحرك",
            badgeTextAr = "شغل فخم",
            symbolText = "✨",
            primaryColor = Color.parseColor("#1D976C"),
            secondaryColor = Color.parseColor("#93F9B9"),
            accentColor = Color.parseColor("#FFFFFF")
        )
    )

    fun ensureBuiltinStickers(context: Context): List<StickerItem> {
        val dir = File(context.filesDir, "builtin_animated_stickers_v2")
        if (!dir.exists()) dir.mkdirs()

        val items = mutableListOf<StickerItem>()
        templates.forEachIndexed { index, tpl ->
            val file = File(dir, "${tpl.id}.webp")
            if (!file.exists() || file.length() == 0L) {
                val frames = (0 until 8).map { frameIdx ->
                    renderAnimatedStickerFrame(tpl, frameIdx, totalFrames = 8)
                }
                val animatedWebpBytes = AnimatedStickerDecoder.encodeFramesToAnimatedWebp(
                    frames = frames,
                    frameDurationMs = 90
                )
                FileOutputStream(file).use { out ->
                    out.write(animatedWebpBytes)
                }
                frames.forEach { it.recycle() }
            }
            items.add(
                StickerItem(
                    id = tpl.id,
                    name = "${tpl.titleAr}.webp",
                    uri = Uri.fromFile(file),
                    source = StickerSource.BUILTIN_PACK,
                    mimeType = "image/webp",
                    sizeBytes = file.length(),
                    lastModified = file.lastModified() + index * 1000L,
                    isAnimated = true,
                    isFavorite = index < 2
                )
            )
        }
        return items
    }

    private fun renderAnimatedStickerFrame(
        tpl: StickerTemplate,
        frameIndex: Int,
        totalFrames: Int
    ): Bitmap {
        val size = 384
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val phase = (frameIndex.toFloat() / totalFrames.toFloat()) * 2f * PI.toFloat()
        val pulse = 1f + 0.08f * sin(phase)
        val bounceY = -12f * sin(phase)

        val cx = size / 2f
        val cy = size / 2f - 20f + bounceY
        val baseRadius = 128f * pulse

        // White sticker die-cut outline
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, baseRadius + 14f, outlinePaint)

        // Inner vibrant gradient circle
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - baseRadius,
                cy - baseRadius,
                cx + baseRadius,
                cy + baseRadius,
                tpl.primaryColor,
                tpl.secondaryColor,
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, baseRadius, fillPaint)

        // Animated orbiting stars inside the sticker itself
        val starRadius = 14f + 4f * cos(phase)
        drawStar(canvas, cx - 105f, cy - 95f + 6f * sin(phase), starRadius, tpl.accentColor)
        drawStar(canvas, cx + 108f, cy - 82f - 6f * cos(phase), starRadius * 0.85f, Color.WHITE)

        // Central emoji/symbol animated inside the sticker
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 108f * pulse
        }
        val fontMetrics = emojiPaint.fontMetrics
        val textOffset = (fontMetrics.descent + fontMetrics.ascent) / 2
        canvas.drawText(tpl.symbolText, cx, cy - textOffset, emojiPaint)

        // Bottom sticker ribbon badge
        val ribbonRect = RectF(48f, 276f, 336f, 352f)
        val ribbonOutlineRect = RectF(40f, 268f, 344f, 360f)
        canvas.drawRoundRect(ribbonOutlineRect, 42f, 42f, outlinePaint)

        val ribbonFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                ribbonRect.left,
                ribbonRect.top,
                ribbonRect.right,
                ribbonRect.bottom,
                tpl.primaryColor,
                tpl.secondaryColor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(ribbonRect, 36f, 36f, ribbonFillPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelMetrics = labelPaint.fontMetrics
        val labelOffset = (labelMetrics.descent + labelMetrics.ascent) / 2
        canvas.drawText(tpl.badgeTextAr, cx, ribbonRect.centerY() - labelOffset, labelPaint)

        return bitmap
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        val path = Path()
        val inner = radius * 0.4f
        for (i in 0 until 8) {
            val r = if (i % 2 == 0) radius else inner
            val angle = i * PI / 4.0
            val x = (cx + r * cos(angle)).toFloat()
            val y = (cy + r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, p)
    }
}
