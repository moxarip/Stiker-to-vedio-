package com.example.video

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.ExportedVideoItem
import com.example.model.MotionEffect
import com.example.model.ParticleOverlay
import com.example.model.StickerItem
import com.example.model.VideoBackgroundStyle
import com.example.model.VideoStudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class LoadedStickerAsset(
    val item: StickerItem,
    val fallbackBitmap: Bitmap,
    private val rawBytes: ByteArray?
) {
    @Suppress("DEPRECATION")
    private val movie: Movie? = try {
        if (rawBytes != null && rawBytes.isNotEmpty()) {
            Movie.decodeByteArray(rawBytes, 0, rawBytes.size)?.takeIf { it.duration() > 0 }
        } else null
    } catch (_: Exception) {
        null
    }

    fun getBitmapAtTime(timeMs: Long): Bitmap {
        val m = movie ?: return fallbackBitmap
        val duration = m.duration().coerceAtLeast(100)
        val relTime = (timeMs % duration).toInt()
        val w = m.width().coerceAtLeast(1)
        val h = m.height().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        m.setTime(relTime)
        m.draw(c, 0f, 0f)
        return bmp
    }
}

object StickerVideoEncoder {

    fun loadStickerAsset(context: Context, item: StickerItem): LoadedStickerAsset? {
        return try {
            val bytes = context.contentResolver.openInputStream(item.uri)?.use { it.readBytes() }
                ?: item.uri.path?.let { File(it).takeIf { f -> f.exists() }?.readBytes() }
                ?: return null

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, opts)
                ?: return null

            LoadedStickerAsset(
                item = item,
                fallbackBitmap = bitmap,
                rawBytes = if (item.isAnimated) bytes else null
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Renders a single studio frame onto any Android [Canvas].
     * Used by BOTH the live 60fps Jetpack Compose preview and the MediaCodec MP4 encoder.
     */
    fun drawStudioFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        assets: List<LoadedStickerAsset>,
        config: VideoStudioConfig,
        timeSeconds: Float,
        totalDurationSeconds: Float
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val minDim = min(w, h)
        val scaledTime = timeSeconds * config.speedMultiplier
        val normalizedProgress = (timeSeconds / totalDurationSeconds.coerceAtLeast(0.5f)).coerceIn(0f, 1f)

        // 1. Draw Background
        drawBackground(canvas, w, h, config.backgroundStyle, scaledTime)

        // 2. Draw Particle Underlay/Overlay
        if (config.particleOverlay != ParticleOverlay.NONE) {
            drawParticles(canvas, w, h, config.particleOverlay, scaledTime, isBackgroundLayer = true)
        }

        // 3. Determine active sticker (supports single sticker or multi-sticker sequence)
        if (assets.isNotEmpty()) {
            val count = assets.size
            val segmentDuration = (totalDurationSeconds / count).coerceAtLeast(0.5f)
            val activeIndex = ((timeSeconds / segmentDuration).toInt()).coerceIn(0, count - 1)
            val localSegmentTime = timeSeconds - (activeIndex * segmentDuration)
            val segmentProgress = (localSegmentTime / segmentDuration).coerceIn(0f, 1f)

            val activeAsset = assets[activeIndex]
            val frameBitmap = activeAsset.getBitmapAtTime((scaledTime * 1000f).toLong())

            // Calculate motion transform
            var scale = 1f
            var rotationDeg = 0f
            var offsetX = 0f
            var offsetY = 0f

            // Smooth entry transition when multiple stickers are sequenced
            if (count > 1 && segmentProgress < 0.15f) {
                val entryT = (segmentProgress / 0.15f).coerceIn(0f, 1f)
                scale *= (0.4f + 0.6f * sin(entryT * PI.toFloat() / 2f))
            } else if (count > 1 && segmentProgress > 0.88f) {
                val exitT = ((1f - segmentProgress) / 0.12f).coerceIn(0f, 1f)
                scale *= (0.5f + 0.5f * exitT)
            }

            when (config.motionEffect) {
                MotionEffect.BOUNCE -> {
                    val bounceWave = abs(sin(scaledTime * PI.toFloat() * 1.8f))
                    offsetY = -minDim * 0.075f * bounceWave
                    scale *= (0.94f + 0.12f * bounceWave)
                }
                MotionEffect.PULSE_GLOW -> {
                    val pulse = sin(scaledTime * PI.toFloat() * 2.2f)
                    scale *= (1.0f + 0.11f * pulse)
                    rotationDeg = 3.5f * sin(scaledTime * PI.toFloat() * 1.1f)
                }
                MotionEffect.ZOOM_CINEMATIC -> {
                    val zoomWave = 0.82f + 0.28f * (0.5f + 0.5f * sin(scaledTime * PI.toFloat() * 1.2f - PI.toFloat() / 2f))
                    scale *= zoomWave
                    rotationDeg = 5f * sin(scaledTime * PI.toFloat() * 0.8f)
                }
                MotionEffect.SWING_DANCE -> {
                    rotationDeg = 14f * sin(scaledTime * PI.toFloat() * 2.4f)
                    offsetX = minDim * 0.045f * cos(scaledTime * PI.toFloat() * 2.4f)
                    scale *= (0.97f + 0.06f * abs(sin(scaledTime * PI.toFloat() * 2.4f)))
                }
                MotionEffect.SPIN_3D -> {
                    rotationDeg = (scaledTime * 180f) % 360f
                    scale *= (0.92f + 0.12f * sin(scaledTime * PI.toFloat() * 2f))
                }
                MotionEffect.FLOAT_WAVE -> {
                    offsetY = minDim * 0.05f * sin(scaledTime * PI.toFloat() * 1.4f)
                    offsetX = minDim * 0.03f * cos(scaledTime * PI.toFloat() * 0.9f)
                    rotationDeg = 4f * sin(scaledTime * PI.toFloat() * 1.4f)
                }
                MotionEffect.NONE -> {
                    // Keep original sticker coordinates
                }
            }

            val cx = w / 2f + offsetX
            val cy = (if (config.captionText.isNotBlank()) h * 0.45f else h * 0.49f) + offsetY

            // Optional glowing sticker aura
            if (config.showStickerGlowBorder && config.backgroundStyle != VideoBackgroundStyle.GREEN_SCREEN) {
                val glowRadius = minDim * 0.34f * scale
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        cx,
                        cy,
                        glowRadius.coerceAtLeast(10f),
                        intArrayOf(
                            Color.argb(110, 37, 211, 102),
                            Color.argb(45, 0, 229, 255),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.65f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawCircle(cx, cy, glowRadius, glowPaint)
            }

            // Draw the sticker bitmap centered at (cx, cy)
            val targetMaxBox = minDim * 0.62f * scale
            val bmpW = frameBitmap.width.coerceAtLeast(1).toFloat()
            val bmpH = frameBitmap.height.coerceAtLeast(1).toFloat()
            val fitScale = min(targetMaxBox / bmpW, targetMaxBox / bmpH)

            val matrix = Matrix().apply {
                postTranslate(-bmpW / 2f, -bmpH / 2f)
                postScale(fitScale, fitScale)
                postRotate(rotationDeg)
                postTranslate(cx, cy)
            }
            val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(frameBitmap, matrix, bmpPaint)

            // Multi-sticker sequence indicator dots at top
            if (count > 1) {
                drawSequenceIndicator(canvas, w, count, activeIndex)
            }
        }

        // 4. Draw Foreground Particles
        if (config.particleOverlay != ParticleOverlay.NONE) {
            drawParticles(canvas, w, h, config.particleOverlay, scaledTime, isBackgroundLayer = false)
        }

        // 5. Draw Custom Caption Overlay if provided
        if (config.captionText.isNotBlank()) {
            drawCaptionOverlay(canvas, w, h, minDim, config.captionText, scaledTime)
        }

        // 6. Subtle timeline progress bar at very bottom (except on green screen)
        if (config.backgroundStyle != VideoBackgroundStyle.GREEN_SCREEN) {
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(140, 37, 211, 102)
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, h - 6f, w * normalizedProgress, h, barPaint)
        }
    }

    private fun drawBackground(
        canvas: Canvas,
        w: Float,
        h: Float,
        bgStyle: VideoBackgroundStyle,
        timeSec: Float
    ) {
        if (bgStyle == VideoBackgroundStyle.GREEN_SCREEN) {
            canvas.drawColor(bgStyle.primaryColorHex)
            return
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                w,
                h,
                bgStyle.primaryColorHex,
                bgStyle.secondaryColorHex,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Subtle WhatsApp chat geometric pattern for WHATSAPP_DARK
        if (bgStyle == VideoBackgroundStyle.WHATSAPP_DARK) {
            val patternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(18, 255, 255, 255)
                this.style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            val step = 110f
            val drift = (timeSec * 16f) % step
            var y = -step + drift
            while (y < h + step) {
                var x = -step + drift
                while (x < w + step) {
                    canvas.drawCircle(x, y, 16f, patternPaint)
                    canvas.drawRoundRect(
                        RectF(x + 42f, y + 32f, x + 78f, y + 58f),
                        8f,
                        8f,
                        patternPaint
                    )
                    x += step
                }
                y += step
            }
        }
    }

    private fun drawParticles(
        canvas: Canvas,
        w: Float,
        h: Float,
        overlay: ParticleOverlay,
        timeSec: Float,
        isBackgroundLayer: Boolean
    ) {
        val count = if (isBackgroundLayer) 14 else 10
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until count) {
            val seed = (i * 73 + if (isBackgroundLayer) 19 else 53).toFloat()
            val normX = ((seed * 37f) % 100f) / 100f
            val speed = 0.22f + ((i % 5) * 0.08f)
            val cycle = (timeSec * speed + (seed % 10f) / 10f) % 1f

            val x = w * normX + sin(timeSec * 2f + i) * 24f
            val y = when (overlay) {
                ParticleOverlay.CONFETTI -> h * cycle
                else -> h * (1f - cycle)
            }
            val size = (if (isBackgroundLayer) 12f else 18f) + (i % 4) * 4f
            val alpha = ((1f - abs(cycle - 0.5f) * 1.8f).coerceIn(0.15f, 1f) * 220).toInt()

            when (overlay) {
                ParticleOverlay.SPARKLES -> {
                    paint.color = if (i % 2 == 0) Color.argb(alpha, 255, 235, 59) else Color.argb(alpha, 0, 229, 255)
                    drawFourPointStar(canvas, x, y, size, paint)
                }
                ParticleOverlay.CONFETTI -> {
                    val colors = intArrayOf(
                        Color.rgb(37, 211, 102),
                        Color.rgb(0, 229, 255),
                        Color.rgb(255, 179, 0),
                        Color.rgb(255, 64, 129)
                    )
                    paint.color = colors[i % colors.size]
                    paint.alpha = alpha
                    canvas.save()
                    canvas.rotate((timeSec * 120f + i * 45f) % 360f, x, y)
                    canvas.drawRoundRect(RectF(x - size, y - size * 0.45f, x + size, y + size * 0.45f), 4f, 4f, paint)
                    canvas.restore()
                }
                ParticleOverlay.HEARTS -> {
                    paint.color = if (i % 2 == 0) Color.argb(alpha, 255, 64, 129) else Color.argb(alpha, 255, 128, 171)
                    canvas.drawCircle(x - size * 0.35f, y, size * 0.45f, paint)
                    canvas.drawCircle(x + size * 0.35f, y, size * 0.45f, paint)
                    val path = Path().apply {
                        moveTo(x - size * 0.78f, y + size * 0.1f)
                        lineTo(x + size * 0.78f, y + size * 0.1f)
                        lineTo(x, y + size * 0.95f)
                        close()
                    }
                    canvas.drawPath(path, paint)
                }
                ParticleOverlay.FIRE_EMBERS -> {
                    paint.color = if (i % 2 == 0) Color.argb(alpha, 255, 152, 0) else Color.argb(alpha, 255, 87, 34)
                    canvas.drawCircle(x, y, size * 0.55f, paint)
                }
                ParticleOverlay.NONE -> Unit
            }
        }
    }

    private fun drawFourPointStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val path = Path()
        val inner = radius * 0.32f
        for (i in 0 until 8) {
            val r = if (i % 2 == 0) radius else inner
            val angle = i * PI / 4.0
            val px = (cx + r * cos(angle)).toFloat()
            val py = (cy + r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawSequenceIndicator(canvas: Canvas, w: Float, count: Int, activeIndex: Int) {
        val dotRadius = 7f
        val spacing = 22f
        val totalWidth = (count - 1) * spacing
        val startX = (w - totalWidth) / 2f
        val y = 38f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until count) {
            paint.color = if (i == activeIndex) {
                Color.rgb(37, 211, 102)
            } else {
                Color.argb(100, 255, 255, 255)
            }
            val r = if (i == activeIndex) dotRadius * 1.35f else dotRadius
            canvas.drawCircle(startX + i * spacing, y, r, paint)
        }
    }

    private fun drawCaptionOverlay(
        canvas: Canvas,
        w: Float,
        h: Float,
        minDim: Float,
        caption: String,
        timeSec: Float
    ) {
        val textSizePx = minDim * 0.068f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = textSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textWidth = textPaint.measureText(caption)
        val padH = minDim * 0.06f
        val padV = minDim * 0.028f
        val boxW = (textWidth + padH * 2f).coerceAtMost(w * 0.9f)
        val boxH = textSizePx + padV * 2f

        val pulseOffset = sin(timeSec * PI.toFloat() * 2f) * 4f
        val centerY = h * 0.86f + pulseOffset
        val rect = RectF(
            w / 2f - boxW / 2f,
            centerY - boxH / 2f,
            w / 2f + boxW / 2f,
            centerY + boxH / 2f
        )

        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 11, 20, 26)
            style = Paint.Style.FILL
        }
        val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 168, 132)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRoundRect(rect, boxH / 2f, boxH / 2f, pillBgPaint)
        canvas.drawRoundRect(rect, boxH / 2f, boxH / 2f, pillStrokePaint)

        val fm = textPaint.fontMetrics
        val baseline = rect.centerY() - (fm.descent + fm.ascent) / 2f
        canvas.drawText(caption, w / 2f, baseline, textPaint)
    }

    /**
     * Encodes the selected stickers and studio configuration into a real H.264 `.mp4` video file
     * using Android's [MediaCodec] and [MediaMuxer].
     */
    suspend fun encodeStickersToMp4(
        context: Context,
        stickers: List<StickerItem>,
        config: VideoStudioConfig,
        outputDir: File,
        onProgress: (Float) -> Unit
    ): Result<ExportedVideoItem> = withContext(Dispatchers.Default) {
        try {
            val loadedAssets = stickers.mapNotNull { loadStickerAsset(context, it) }
            if (loadedAssets.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("لم يتم العثور على ملصقات صالحة للتحويل"))
            }

            if (!outputDir.exists()) outputDir.mkdirs()

            val width = config.aspectRatio.widthPx
            val height = config.aspectRatio.heightPx
            val fps = config.fps
            val durationSec = config.durationSeconds.coerceIn(2, 15)
            val totalFrames = durationSec * fps

            val fileName = "StickerVideo_${durationSec}s_${width}x${height}_${loadedAssets.size}stk_${System.currentTimeMillis()}.mp4"
            val outputFile = File(outputDir, fileName)

            val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
            val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 3_500_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encoder = MediaCodec.createEncoderByType(mimeType)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()
            var trackIndex = -1
            var muxerStarted = false

            try {
                for (frameIdx in 0 until totalFrames) {
                    coroutineContext.ensureActive()

                    val timeSec = frameIdx.toFloat() / fps.toFloat()
                    val surfaceCanvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            inputSurface.lockHardwareCanvas()
                        } catch (_: Exception) {
                            inputSurface.lockCanvas(null)
                        }
                    } else {
                        inputSurface.lockCanvas(null)
                    }

                    try {
                        drawStudioFrame(
                            canvas = surfaceCanvas,
                            width = width,
                            height = height,
                            assets = loadedAssets,
                            config = config,
                            timeSeconds = timeSec,
                            totalDurationSeconds = durationSec.toFloat()
                        )
                    } finally {
                        inputSurface.unlockCanvasAndPost(surfaceCanvas)
                    }

                    drainEncoder(encoder, muxer, bufferInfo, endOfStream = false, trackIndex, muxerStarted).let { (newTrack, started) ->
                        trackIndex = newTrack
                        muxerStarted = started
                    }

                    onProgress((frameIdx + 1).toFloat() / totalFrames.toFloat())
                }

                // Signal End-Of-Stream
                drainEncoder(encoder, muxer, bufferInfo, endOfStream = true, trackIndex, muxerStarted)
            } finally {
                try {
                    inputSurface.release()
                } catch (_: Exception) {}
                try {
                    encoder.stop()
                    encoder.release()
                } catch (_: Exception) {}
                try {
                    if (muxerStarted) muxer.stop()
                    muxer.release()
                } catch (_: Exception) {}
            }

            val exportedItem = ExportedVideoItem(
                id = outputFile.name,
                file = outputFile,
                title = "فيديو ملصق (${width}x${height})",
                durationSec = durationSec,
                resolution = "${width}x${height}",
                sizeBytes = outputFile.length(),
                createdAt = outputFile.lastModified(),
                stickerCount = loadedAssets.size
            )
            Result.success(exportedItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        currentTrackIndex: Int,
        currentMuxerStarted: Boolean
    ): Pair<Int, Boolean> {
        var trackIndex = currentTrackIndex
        var muxerStarted = currentMuxerStarted

        if (endOfStream) {
            encoder.signalEndOfInputStream()
        }

        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    val newFormat = encoder.outputFormat
                    trackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                }
            } else if (status >= 0) {
                val encodedData = encoder.getOutputBuffer(status)
                if (encodedData != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(status, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
        return trackIndex to muxerStarted
    }

    suspend fun saveVideoToPublicGallery(context: Context, videoFile: File): Uri? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/StickerVideoStudio"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val itemUri = resolver.insert(collection, values) ?: return@withContext null
            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(videoFile).use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(itemUri, completeValues, null, null)
            }
            itemUri
        } catch (_: Exception) {
            null
        }
    }

    fun shareFileViaProvider(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String,
        targetPackage: String? = null
    ) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (targetPackage != null) {
                    setPackage(targetPackage)
                }
            }
            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            // Fallback without package restriction if WhatsApp is not installed
            if (targetPackage != null) {
                shareFileViaProvider(context, file, mimeType, chooserTitle, targetPackage = null)
            }
        }
    }
}
