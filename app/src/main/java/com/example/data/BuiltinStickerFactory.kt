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
import android.os.Build
import com.example.model.StickerItem
import com.example.model.StickerSource
import java.io.File
import java.io.FileOutputStream

object BuiltinStickerFactory {

    private data class StickerTemplate(
        val id: String,
        val titleAr: String,
        val badgeTextAr: String,
        val symbolText: String,
        val primaryColor: Int,
        val secondaryColor: Int,
        val accentColor: Int,
        val isAnimatedTag: Boolean
    )

    private val templates = listOf(
        StickerTemplate(
            id = "sticker_fire_hype",
            titleAr = "حماس ناري",
            badgeTextAr = "عاش يا بطل!",
            symbolText = "🔥",
            primaryColor = Color.parseColor("#FF512F"),
            secondaryColor = Color.parseColor("#F09819"),
            accentColor = Color.parseColor("#FFF176"),
            isAnimatedTag = true
        ),
        StickerTemplate(
            id = "sticker_laugh_joy",
            titleAr = "ضحكة من القلب",
            badgeTextAr = "ههههه رهيييب",
            symbolText = "😂",
            primaryColor = Color.parseColor("#00A884"),
            secondaryColor = Color.parseColor("#25D366"),
            accentColor = Color.parseColor("#FFFFFF"),
            isAnimatedTag = true
        ),
        StickerTemplate(
            id = "sticker_neon_heart",
            titleAr = "قلب متوهج",
            badgeTextAr = "يسعد قلبك",
            symbolText = "❤️",
            primaryColor = Color.parseColor("#EC008C"),
            secondaryColor = Color.parseColor("#FC6767"),
            accentColor = Color.parseColor("#FFD1DC"),
            isAnimatedTag = true
        ),
        StickerTemplate(
            id = "sticker_golden_cup",
            titleAr = "كأس الصدارة",
            badgeTextAr = "رقم واحد #1",
            symbolText = "🏆",
            primaryColor = Color.parseColor("#F7971E"),
            secondaryColor = Color.parseColor("#FFD200"),
            accentColor = Color.parseColor("#FFF9C4"),
            isAnimatedTag = false
        ),
        StickerTemplate(
            id = "sticker_coffee_morning",
            titleAr = "قهوة الصباح",
            badgeTextAr = "صباح الروقان",
            symbolText = "☕",
            primaryColor = Color.parseColor("#603813"),
            secondaryColor = Color.parseColor("#B29F94"),
            accentColor = Color.parseColor("#FFF8E1"),
            isAnimatedTag = false
        ),
        StickerTemplate(
            id = "sticker_party_congrats",
            titleAr = "ألف مبروك",
            badgeTextAr = "ألف مبروك!",
            symbolText = "🎉",
            primaryColor = Color.parseColor("#7F00FF"),
            secondaryColor = Color.parseColor("#E100FF"),
            accentColor = Color.parseColor("#80D8FF"),
            isAnimatedTag = true
        ),
        StickerTemplate(
            id = "sticker_super_like",
            titleAr = "إعجاب أسطوري",
            badgeTextAr = "كفو والله",
            symbolText = "👍",
            primaryColor = Color.parseColor("#0083B0"),
            secondaryColor = Color.parseColor("#00B4DB"),
            accentColor = Color.parseColor("#E0F7FA"),
            isAnimatedTag = false
        ),
        StickerTemplate(
            id = "sticker_star_magic",
            titleAr = "نجوم الإبداع",
            badgeTextAr = "شغل فخم",
            symbolText = "✨",
            primaryColor = Color.parseColor("#1D976C"),
            secondaryColor = Color.parseColor("#93F9B9"),
            accentColor = Color.parseColor("#FFFFFF"),
            isAnimatedTag = true
        )
    )

    fun ensureBuiltinStickers(context: Context): List<StickerItem> {
        val dir = File(context.filesDir, "builtin_stickers")
        if (!dir.exists()) dir.mkdirs()

        val items = mutableListOf<StickerItem>()
        templates.forEachIndexed { index, tpl ->
            val file = File(dir, "${tpl.id}.webp")
            if (!file.exists() || file.length() == 0L) {
                val bmp = renderStickerBitmap(tpl)
                FileOutputStream(file).use { out ->
                    @Suppress("DEPRECATION")
                    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSLESS
                    } else {
                        Bitmap.CompressFormat.WEBP
                    }
                    bmp.compress(format, 95, out)
                }
                bmp.recycle()
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
                    isAnimated = tpl.isAnimatedTag,
                    isFavorite = index < 2
                )
            )
        }
        return items
    }

    private fun renderStickerBitmap(tpl: StickerTemplate): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f - 28f

        // White sticker die-cut outline
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(16f, 0f, 6f, Color.argb(110, 0, 0, 0))
        }
        canvas.drawCircle(cx, cy, 178f, outlinePaint)

        // Inner vibrant gradient circle
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cx - 160f,
                cy - 160f,
                cx + 160f,
                cy + 160f,
                tpl.primaryColor,
                tpl.secondaryColor,
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, 158f, fillPaint)

        // Decorative inner ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tpl.accentColor
            alpha = 110
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(cx, cy, 138f, ringPaint)

        // Sparkle stars around sticker
        drawStar(canvas, cx - 135f, cy - 125f, 18f, tpl.accentColor)
        drawStar(canvas, cx + 140f, cy - 110f, 14f, Color.WHITE)
        drawStar(canvas, cx - 145f, cy + 75f, 12f, Color.WHITE)

        // Central emoji/symbol
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 148f
        }
        val fontMetrics = emojiPaint.fontMetrics
        val textOffset = (fontMetrics.descent + fontMetrics.ascent) / 2
        canvas.drawText(tpl.symbolText, cx, cy - textOffset, emojiPaint)

        // Bottom sticker ribbon badge with white border
        val ribbonRect = RectF(64f, 366f, 448f, 468f)
        val ribbonOutlineRect = RectF(54f, 356f, 458f, 478f)
        canvas.drawRoundRect(ribbonOutlineRect, 56f, 56f, outlinePaint)

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
        canvas.drawRoundRect(ribbonRect, 48f, 48f, ribbonFillPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(6f, 0f, 3f, Color.argb(140, 0, 0, 0))
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
            val angle = i * Math.PI / 4.0
            val x = (cx + r * Math.cos(angle)).toFloat()
            val y = (cy + r * Math.sin(angle)).toFloat()
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
