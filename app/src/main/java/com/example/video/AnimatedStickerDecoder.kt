package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DecodedStickerFrame(
    val bitmap: Bitmap,
    val durationMs: Int
)

data class DecodedAnimatedSticker(
    val width: Int,
    val height: Int,
    val frames: List<DecodedStickerFrame>,
    val isAnimated: Boolean
) {
    val totalDurationMs: Int
        get() = frames.sumOf { it.durationMs.coerceAtLeast(33) }.coerceAtLeast(100)
}

object AnimatedStickerDecoder {

    fun decodeFromUri(context: Context, uri: Uri): DecodedAnimatedSticker? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: uri.path?.let { File(it).takeIf { f -> f.exists() }?.readBytes() }
                ?: return null
            decodeFromBytes(bytes)
        } catch (_: Throwable) {
            null
        }
    }

    fun decodeFromBytes(bytes: ByteArray): DecodedAnimatedSticker? {
        if (bytes.size < 16) return null

        // 1. Check if RIFF WEBP with ANMF chunks (WhatsApp Animated Sticker format)
        if (isWebpBytes(bytes)) {
            val webpResult = decodeAnimatedWebp(bytes)
            if (webpResult != null && webpResult.frames.isNotEmpty()) {
                return webpResult
            }
        }

        // 2. Check if Animated GIF
        if (isGifBytes(bytes)) {
            val gifResult = decodeAnimatedGif(bytes)
            if (gifResult != null && gifResult.frames.isNotEmpty()) {
                return gifResult
            }
        }

        // 3. Fallback: Single static frame (.webp / .png / .jpg)
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rawBmp = BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, opts)
            ?: return null
        val softBmp = ensureSoftwareArgb(rawBmp)
        return DecodedAnimatedSticker(
            width = softBmp.width.coerceAtLeast(1),
            height = softBmp.height.coerceAtLeast(1),
            frames = listOf(DecodedStickerFrame(softBmp, durationMs = 1000)),
            isAnimated = false
        )
    }

    private fun isWebpBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        return bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
    }

    private fun isGifBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 6) return false
        val header = String(bytes, 0, 6, Charsets.US_ASCII)
        return header == "GIF89a" || header == "GIF87a"
    }

    /**
     * Parses RIFF WEBPVP8X + ANIM + ANMF chunks directly according to the WebP Container Spec,
     * reconstructing each ANMF frame into a valid standalone WebP image and compositing it
     * onto the sticker canvas with its exact x, y, blend mode, disposal mode, and frame duration.
     */
    private fun decodeAnimatedWebp(bytes: ByteArray): DecodedAnimatedSticker? {
        try {
            var canvasWidth = 512
            var canvasHeight = 512
            val anmfPayloads = mutableListOf<ByteArray>()

            var offset = 12
            while (offset + 8 <= bytes.size) {
                val fourCc = String(bytes, offset, 4, Charsets.US_ASCII)
                val chunkSize = readInt32LE(bytes, offset + 4)
                if (chunkSize < 0 || offset + 8 + chunkSize > bytes.size) break

                val payloadStart = offset + 8
                when (fourCc) {
                    "VP8X" -> {
                        if (chunkSize >= 10) {
                            canvasWidth = (readUint24LE(bytes, payloadStart + 4) + 1).coerceIn(1, 2048)
                            canvasHeight = (readUint24LE(bytes, payloadStart + 7) + 1).coerceIn(1, 2048)
                        }
                    }
                    "ANMF" -> {
                        if (chunkSize > 16) {
                            val anmfBytes = bytes.copyOfRange(payloadStart, payloadStart + chunkSize)
                            anmfPayloads.add(anmfBytes)
                        }
                    }
                }

                val paddedChunkSize = chunkSize + (chunkSize and 1)
                offset += 8 + paddedChunkSize
            }

            // If no ANMF chunks exist, it's a single-frame static WebP
            if (anmfPayloads.isEmpty()) {
                return null
            }

            val compositedFrames = mutableListOf<DecodedStickerFrame>()
            val accumulatorBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            val accumulatorCanvas = Canvas(accumulatorBitmap)
            val blendSrcOverPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val blendSrcCopyPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
            }
            val clearRectPaint = Paint().apply {
                color = Color.TRANSPARENT
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }

            for (anmf in anmfPayloads) {
                if (anmf.size <= 16) continue
                val frameX = readUint24LE(anmf, 0) * 2
                val frameY = readUint24LE(anmf, 3) * 2
                val frameW = (readUint24LE(anmf, 6) + 1).coerceAtLeast(1)
                val frameH = (readUint24LE(anmf, 9) + 1).coerceAtLeast(1)
                val rawDurationMs = readUint24LE(anmf, 12)
                val durationMs = if (rawDurationMs <= 10) 80 else rawDurationMs.coerceIn(20, 5000)
                val flags = anmf[15].toInt() and 0xFF
                val disposeToBg = (flags and 1) != 0
                val doNotBlend = ((flags shr 1) and 1) != 0

                val subChunks = anmf.copyOfRange(16, anmf.size)
                val singleFrameWebp = buildStandaloneWebpFromAnmfSubchunks(frameW, frameH, subChunks)
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decodedPatch = BitmapFactory.decodeByteArray(singleFrameWebp, 0, singleFrameWebp.size, opts)
                if (decodedPatch != null) {
                    val softPatch = ensureSoftwareArgb(decodedPatch)
                    val dstRect = Rect(frameX, frameY, frameX + frameW, frameY + frameH)
                    if (doNotBlend) {
                        accumulatorCanvas.drawRect(
                            frameX.toFloat(),
                            frameY.toFloat(),
                            (frameX + frameW).toFloat(),
                            (frameY + frameH).toFloat(),
                            clearRectPaint
                        )
                        accumulatorCanvas.drawBitmap(softPatch, null, dstRect, blendSrcCopyPaint)
                    } else {
                        accumulatorCanvas.drawBitmap(softPatch, null, dstRect, blendSrcOverPaint)
                    }

                    // Snapshot the composited canvas for this frame
                    val frameSnapshot = accumulatorBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    compositedFrames.add(DecodedStickerFrame(frameSnapshot, durationMs))

                    if (disposeToBg) {
                        accumulatorCanvas.drawRect(
                            frameX.toFloat(),
                            frameY.toFloat(),
                            (frameX + frameW).toFloat(),
                            (frameY + frameH).toFloat(),
                            clearRectPaint
                        )
                    }
                }
            }

            accumulatorBitmap.recycle()

            if (compositedFrames.isEmpty()) return null
            return DecodedAnimatedSticker(
                width = canvasWidth,
                height = canvasHeight,
                frames = compositedFrames,
                isAnimated = compositedFrames.size > 1
            )
        } catch (_: Throwable) {
            return null
        }
    }

    /**
     * Wraps raw ANMF frame sub-chunks (ALPH + VP8 / VP8L) into a valid standalone RIFF WEBP image
     * so Android's BitmapFactory can decode each animation frame directly.
     */
    private fun buildStandaloneWebpFromAnmfSubchunks(
        frameW: Int,
        frameH: Int,
        subChunks: ByteArray
    ): ByteArray {
        val hasAlphChunk = subChunks.size >= 8 &&
            subChunks[0] == 'A'.code.toByte() &&
            subChunks[1] == 'L'.code.toByte() &&
            subChunks[2] == 'P'.code.toByte() &&
            subChunks[3] == 'H'.code.toByte()

        val padByte = subChunks.size and 1
        return if (hasAlphChunk) {
            val vp8xChunkSize = 18 // 8 byte header + 10 byte payload
            val riffPayloadSize = 4 + vp8xChunkSize + subChunks.size + padByte
            val out = ByteArrayOutputStream(8 + riffPayloadSize)
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            writeInt32LE(out, riffPayloadSize)
            out.write("WEBP".toByteArray(Charsets.US_ASCII))

            out.write("VP8X".toByteArray(Charsets.US_ASCII))
            writeInt32LE(out, 10)
            out.write(0x10) // Alpha flag
            out.write(0)
            out.write(0)
            out.write(0)
            writeUint24LE(out, (frameW - 1).coerceAtLeast(0))
            writeUint24LE(out, (frameH - 1).coerceAtLeast(0))

            out.write(subChunks)
            if (padByte == 1) out.write(0)
            out.toByteArray()
        } else {
            val riffPayloadSize = 4 + subChunks.size + padByte
            val out = ByteArrayOutputStream(8 + riffPayloadSize)
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            writeInt32LE(out, riffPayloadSize)
            out.write("WEBP".toByteArray(Charsets.US_ASCII))
            out.write(subChunks)
            if (padByte == 1) out.write(0)
            out.toByteArray()
        }
    }

    private fun decodeAnimatedGif(bytes: ByteArray): DecodedAnimatedSticker? {
        return try {
            @Suppress("DEPRECATION")
            val movie = Movie.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val duration = movie.duration()
            val w = movie.width().coerceIn(1, 1024)
            val h = movie.height().coerceIn(1, 1024)
            if (duration <= 0) return null

            val stepMs = 66 // ~15 fps sampling for GIF
            val totalSteps = (duration / stepMs).coerceIn(2, 90)
            val frames = mutableListOf<DecodedStickerFrame>()
            for (i in 0 until totalSteps) {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                movie.setTime((i * duration) / totalSteps)
                movie.draw(canvas, 0f, 0f)
                frames.add(DecodedStickerFrame(bmp, durationMs = duration / totalSteps))
            }
            DecodedAnimatedSticker(
                width = w,
                height = h,
                frames = frames,
                isAnimated = frames.size > 1
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Encodes a list of [Bitmap] frames into a genuine multi-frame Animated WebP (`RIFF WEBPVP8X` + `ANIM` + `ANMF`)
     * byte array so built-in sample stickers are real animated `.webp` files.
     */
    fun encodeFramesToAnimatedWebp(frames: List<Bitmap>, frameDurationMs: Int = 85): ByteArray {
        if (frames.isEmpty()) return ByteArray(0)
        val width = frames.first().width.coerceAtLeast(2)
        val height = frames.first().height.coerceAtLeast(2)

        val anmfChunksOut = ByteArrayOutputStream()
        for (bmp in frames) {
            val singleWebp = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            bmp.compress(format, 88, singleWebp)
            val singleBytes = singleWebp.toByteArray()
            val subChunks = extractElementaryWebpSubchunks(singleBytes)
            if (subChunks.isNotEmpty()) {
                val anmfPayloadSize = 16 + subChunks.size
                anmfChunksOut.write("ANMF".toByteArray(Charsets.US_ASCII))
                writeInt32LE(anmfChunksOut, anmfPayloadSize)
                writeUint24LE(anmfChunksOut, 0) // frameX / 2
                writeUint24LE(anmfChunksOut, 0) // frameY / 2
                writeUint24LE(anmfChunksOut, width - 1)
                writeUint24LE(anmfChunksOut, height - 1)
                writeUint24LE(anmfChunksOut, frameDurationMs)
                // Flags: bit 1 = doNotBlend (overwrite frame), bit 0 = disposeToBackground
                anmfChunksOut.write(0x03)
                anmfChunksOut.write(subChunks)
                if ((anmfPayloadSize and 1) == 1) {
                    anmfChunksOut.write(0)
                }
            }
        }

        val allAnmfBytes = anmfChunksOut.toByteArray()
        // VP8X (18 bytes) + ANIM (14 bytes) + allAnmfBytes
        val riffPayloadSize = 4 + 18 + 14 + allAnmfBytes.size
        val finalOut = ByteArrayOutputStream(8 + riffPayloadSize)

        finalOut.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeInt32LE(finalOut, riffPayloadSize)
        finalOut.write("WEBP".toByteArray(Charsets.US_ASCII))

        // VP8X chunk: flags 0x12 (Alpha 0x10 + Animation 0x02)
        finalOut.write("VP8X".toByteArray(Charsets.US_ASCII))
        writeInt32LE(finalOut, 10)
        finalOut.write(0x12)
        finalOut.write(0)
        finalOut.write(0)
        finalOut.write(0)
        writeUint24LE(finalOut, width - 1)
        writeUint24LE(finalOut, height - 1)

        // ANIM chunk: 6 bytes payload (BG color 0x00000000 + loop count 0 = infinite)
        finalOut.write("ANIM".toByteArray(Charsets.US_ASCII))
        writeInt32LE(finalOut, 6)
        writeInt32LE(finalOut, 0) // transparent BGRA
        finalOut.write(0) // loop count low
        finalOut.write(0) // loop count high

        finalOut.write(allAnmfBytes)
        return finalOut.toByteArray()
    }

    private fun extractElementaryWebpSubchunks(singleWebp: ByteArray): ByteArray {
        if (!isWebpBytes(singleWebp)) return ByteArray(0)
        val out = ByteArrayOutputStream()
        var offset = 12
        while (offset + 8 <= singleWebp.size) {
            val fourCc = String(singleWebp, offset, 4, Charsets.US_ASCII)
            val chunkSize = readInt32LE(singleWebp, offset + 4)
            if (chunkSize < 0 || offset + 8 + chunkSize > singleWebp.size) break
            val paddedSize = chunkSize + (chunkSize and 1)
            if (fourCc == "ALPH" || fourCc == "VP8 " || fourCc == "VP8L") {
                val totalChunkBytes = 8 + paddedSize
                val safeEnd = (offset + totalChunkBytes).coerceAtMost(singleWebp.size)
                out.write(singleWebp, offset, safeEnd - offset)
                if (((safeEnd - offset) and 1) == 1) {
                    out.write(0)
                }
            }
            offset += 8 + paddedSize
        }
        return out.toByteArray()
    }

    private fun ensureSoftwareArgb(bmp: Bitmap): Bitmap {
        return if (bmp.config != Bitmap.Config.ARGB_8888) {
            bmp.copy(Bitmap.Config.ARGB_8888, false) ?: bmp
        } else {
            bmp
        }
    }

    private fun readInt32LE(bytes: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readUint24LE(bytes: ByteArray, offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16)
    }

    private fun writeInt32LE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun writeUint24LE(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
    }
}
