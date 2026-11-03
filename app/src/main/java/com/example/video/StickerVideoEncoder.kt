package com.example.video

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.ExportedVideoItem
import com.example.model.StickerItem
import com.example.model.VideoStudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.min

class LoadedStickerAsset(
    val item: StickerItem,
    val decodedSticker: DecodedAnimatedSticker
) {
    val frameCount: Int
        get() = decodedSticker.frames.size

    val totalDurationMs: Int
        get() = decodedSticker.totalDurationMs

    val width: Int
        get() = decodedSticker.width

    val height: Int
        get() = decodedSticker.height

    val isAnimated: Boolean
        get() = decodedSticker.isAnimated

    /**
     * Returns the exact original sticker frame at [timeMs] without any modification.
     */
    fun getOriginalFrameAtTime(timeMs: Long): Bitmap {
        val frames = decodedSticker.frames
        if (frames.size <= 1) return frames.first().bitmap
        val loopDuration = totalDurationMs.coerceAtLeast(1)
        val elapsedInLoop = (timeMs % loopDuration).toInt()
        var acc = 0
        for (frame in frames) {
            acc += frame.durationMs.coerceAtLeast(20)
            if (elapsedInLoop < acc) {
                return frame.bitmap
            }
        }
        return frames.last().bitmap
    }
}

object StickerVideoEncoder {

    fun loadStickerAsset(context: Context, item: StickerItem): LoadedStickerAsset? {
        val decoded = AnimatedStickerDecoder.decodeFromUri(context, item.uri) ?: return null
        if (decoded.frames.isEmpty()) return null
        return LoadedStickerAsset(item = item, decodedSticker = decoded)
    }

    /**
     * Draws the exact original frame of the animated sticker at [timeSeconds] onto [canvas]
     * without adding any artificial effects, motion, particles, or overlays.
     */
    fun drawStudioFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        assets: List<LoadedStickerAsset>,
        @Suppress("UNUSED_PARAMETER") config: VideoStudioConfig,
        timeSeconds: Float,
        totalDurationSeconds: Float
    ) {
        val w = width.coerceAtLeast(1).toFloat()
        val h = height.coerceAtLeast(1).toFloat()

        // Pure neutral background (since MP4 H.264 does not have an alpha channel)
        canvas.drawColor(Color.BLACK)

        if (assets.isEmpty()) return

        val count = assets.size
        val segmentDuration = (totalDurationSeconds / count).coerceAtLeast(0.1f)
        val activeIndex = ((timeSeconds / segmentDuration).toInt()).coerceIn(0, count - 1)
        val localTimeSec = timeSeconds - (activeIndex * segmentDuration)

        val activeAsset = assets[activeIndex]
        val frameBitmap = activeAsset.getOriginalFrameAtTime((localTimeSec * 1000f).toLong())

        val bmpW = frameBitmap.width.coerceAtLeast(1).toFloat()
        val bmpH = frameBitmap.height.coerceAtLeast(1).toFloat()
        val scale = min(w / bmpW, h / bmpH)
        val drawW = bmpW * scale
        val drawH = bmpH * scale
        val left = (w - drawW) / 2f
        val top = (h - drawH) / 2f

        val dstRect = RectF(left, top, left + drawW, top + drawH)
        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(frameBitmap, null, dstRect, bmpPaint)
    }

    /**
     * Directly converts the animated sticker(s) (.webp / .gif) into a pure `.mp4` (H.264 AVC) video
     * preserving the exact original animation frames and timings with zero artificial modifications.
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

            // Preserve the sticker's native dimensions (WhatsApp stickers are 512x512, which is a multiple of 16)
            val primaryAsset = loadedAssets.first()
            val width = ((primaryAsset.width.coerceIn(256, 720) + 15) / 16) * 16
            val height = ((primaryAsset.height.coerceIn(256, 720) + 15) / 16) * 16
            val fps = 25

            // Calculate exact original animation duration (repeat short sticker loops so MP4 plays smoothly in WhatsApp/Gallery)
            val rawStickerTotalMs = loadedAssets.sumOf { it.totalDurationMs }
            val durationMs = if (rawStickerTotalMs < 2000) {
                val repeats = (2400 / rawStickerTotalMs.coerceAtLeast(200)).coerceIn(1, 6)
                rawStickerTotalMs * repeats
            } else {
                rawStickerTotalMs.coerceAtMost(15_000)
            }
            val durationSec = (durationMs / 1000).coerceAtLeast(1)
            val totalFrames = ((durationMs * fps) / 1000).coerceAtLeast(fps)

            val fileName = "Sticker_${durationSec}s_${width}x${height}_${loadedAssets.size}stk_${System.currentTimeMillis()}.mp4"
            val outputFile = File(outputDir, fileName)

            val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
            val codecInfo = selectAvcCodecInfo(mimeType)
            val colorFormat = selectSupportedYuvColorFormat(codecInfo, mimeType)

            val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val encoder = if (codecInfo != null) {
                MediaCodec.createByCodecName(codecInfo.name)
            } else {
                MediaCodec.createEncoderByType(mimeType)
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val isPlanar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar ||
                colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()
            var trackIndex = -1
            var muxerStarted = false

            val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val frameCanvas = Canvas(frameBitmap)
            val argbPixels = IntArray(width * height)
            val yuvData = ByteArray(width * height * 3 / 2)
            val totalDurationSeconds = durationMs / 1000f

            try {
                for (frameIdx in 0 until totalFrames) {
                    coroutineContext.ensureActive()

                    val timeSec = frameIdx.toFloat() / fps.toFloat()
                    frameCanvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
                    drawStudioFrame(
                        canvas = frameCanvas,
                        width = width,
                        height = height,
                        assets = loadedAssets,
                        config = config,
                        timeSeconds = timeSec,
                        totalDurationSeconds = totalDurationSeconds
                    )

                    frameBitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)
                    convertArgbToYuv420(argbPixels, yuvData, width, height, isPlanar)

                    var frameQueued = false
                    var retries = 0
                    while (!frameQueued && retries < 100) {
                        val inputBufIdx = encoder.dequeueInputBuffer(10_000L)
                        if (inputBufIdx >= 0) {
                            val ptsUs = (frameIdx * 1_000_000L) / fps.toLong()
                            val inputBuffer = encoder.getInputBuffer(inputBufIdx)
                            if (inputBuffer != null) {
                                inputBuffer.clear()
                                val bytesToWrite = min(inputBuffer.remaining(), yuvData.size)
                                inputBuffer.put(yuvData, 0, bytesToWrite)
                                encoder.queueInputBuffer(inputBufIdx, 0, bytesToWrite, ptsUs, 0)
                            } else {
                                encoder.queueInputBuffer(inputBufIdx, 0, 0, ptsUs, 0)
                            }
                            frameQueued = true
                        } else {
                            val state = drainEncoderOutput(encoder, muxer, bufferInfo, endOfStream = false, trackIndex, muxerStarted)
                            trackIndex = state.first
                            muxerStarted = state.second
                            retries++
                        }
                    }

                    val state = drainEncoderOutput(encoder, muxer, bufferInfo, endOfStream = false, trackIndex, muxerStarted)
                    trackIndex = state.first
                    muxerStarted = state.second

                    onProgress((frameIdx + 1).toFloat() / totalFrames.toFloat())
                }

                var eosQueued = false
                var eosRetries = 0
                while (!eosQueued && eosRetries < 100) {
                    val inputBufIdx = encoder.dequeueInputBuffer(10_000L)
                    if (inputBufIdx >= 0) {
                        val endPtsUs = (totalFrames * 1_000_000L) / fps.toLong()
                        encoder.queueInputBuffer(
                            inputBufIdx,
                            0,
                            0,
                            endPtsUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        eosQueued = true
                    } else {
                        val state = drainEncoderOutput(encoder, muxer, bufferInfo, endOfStream = false, trackIndex, muxerStarted)
                        trackIndex = state.first
                        muxerStarted = state.second
                        eosRetries++
                    }
                }

                drainEncoderOutput(encoder, muxer, bufferInfo, endOfStream = true, trackIndex, muxerStarted).let {
                    trackIndex = it.first
                    muxerStarted = it.second
                }
            } finally {
                frameBitmap.recycle()
                try {
                    encoder.stop()
                } catch (_: Throwable) {}
                try {
                    encoder.release()
                } catch (_: Throwable) {}
                try {
                    if (muxerStarted) muxer.stop()
                } catch (_: Throwable) {}
                try {
                    muxer.release()
                } catch (_: Throwable) {}
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("فشل تحويل الصيغة إلى MP4"))
            }

            val exportedItem = ExportedVideoItem(
                id = outputFile.name,
                file = outputFile,
                title = loadedAssets.first().item.name.substringBeforeLast('.') + ".mp4",
                durationSec = durationSec,
                resolution = "${width}x${height}",
                sizeBytes = outputFile.length(),
                createdAt = outputFile.lastModified(),
                stickerCount = loadedAssets.size
            )
            Result.success(exportedItem)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun selectAvcCodecInfo(mimeType: String): MediaCodecInfo? {
        return try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            list.codecInfos.firstOrNull { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun selectSupportedYuvColorFormat(codecInfo: MediaCodecInfo?, mimeType: String): Int {
        if (codecInfo == null) {
            return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        }
        return try {
            val caps = codecInfo.getCapabilitiesForType(mimeType)
            val formats = caps.colorFormats
            when {
                formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) ->
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                else -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            }
        } catch (_: Throwable) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        }
    }

    private fun convertArgbToYuv420(
        argb: IntArray,
        yuv: ByteArray,
        width: Int,
        height: Int,
        isPlanar: Boolean
    ) {
        val frameSize = width * height
        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + (frameSize / 4)
        var uvIndex = frameSize

        for (j in 0 until height) {
            val rowOffset = j * width
            val isEvenRow = (j and 1) == 0
            for (i in 0 until width) {
                val pixel = argb[rowOffset + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                if (yIndex < frameSize) {
                    yuv[yIndex++] = y.toByte()
                }

                if (isEvenRow && (i and 1) == 0) {
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    if (isPlanar) {
                        if (uIndex < frameSize + frameSize / 4) yuv[uIndex++] = u.toByte()
                        if (vIndex < yuv.size) yuv[vIndex++] = v.toByte()
                    } else {
                        if (uvIndex + 1 < yuv.size) {
                            yuv[uvIndex++] = u.toByte()
                            yuv[uvIndex++] = v.toByte()
                        }
                    }
                }
            }
        }
    }

    private fun drainEncoderOutput(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        currentTrackIndex: Int,
        currentMuxerStarted: Boolean
    ): Pair<Int, Boolean> {
        var trackIndex = currentTrackIndex
        var muxerStarted = currentMuxerStarted
        var loops = 0

        while (loops < 200) {
            loops++
            val status = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
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
                val encodedData: ByteBuffer? = encoder.getOutputBuffer(status)
                if (encodedData != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(status, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                } else {
                    encoder.releaseOutputBuffer(status, false)
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
        } catch (_: Throwable) {
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
        } catch (_: Throwable) {
            if (targetPackage != null) {
                shareFileViaProvider(context, file, mimeType, chooserTitle, targetPackage = null)
            }
        }
    }
}
